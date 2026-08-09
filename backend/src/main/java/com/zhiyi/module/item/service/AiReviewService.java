package com.zhiyi.module.item.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DeepSeek 内容审核客户端。任何远端异常都会转换为“待人工复核”的降级结果，
 * 不让外部 AI 服务阻塞商品发布主流程。
 */
@Slf4j
@Service
public class AiReviewService {

    private static final String SYSTEM_PROMPT = """
            你是校园二手交易平台的内容审核助手。请同时完成合规审核与商品标签提取。
            以下内容属于违规：危险品或管制物品、代考代写等学术不端服务、人身攻击或侮辱、
            明显欺诈或虚假价格信息、广告引流及其他与校园交易无关的内容。
            必须把价格与标题、型号、成色和描述放在一起判断，不能因为描述措辞自然就忽略异常价格。
            例如：正常使用或接近全新的 iPhone、iPad、MacBook 等高价值数码产品标价 100 元及以下，
            且没有明确说明是保护壳、配件、坏机、零件或维修用途时，应判定为明显虚假价格。
            只返回 JSON 对象，不要输出 Markdown 或解释文字。JSON 必须包含：
            is_violation（布尔值）、violation_reason（字符串，合规时为空字符串）、
            tags（字符串数组，提取 2 至 6 个简短、可搜索的商品标签）。
            """;

    private static final BigDecimal PREMIUM_DEVICE_FLOOR = new BigDecimal("100.00");
    private static final BigDecimal NEW_DIGITAL_FLOOR = new BigDecimal("10.00");
    // 本地违规关键词：不依赖 AI API 即可拦截明显的违规内容
    private static final Pattern VIOLATION_KEYWORDS = Pattern.compile(
            "(?i)(代写|代考|代课|代签到|代理课|替考|作弊|办假证|办证|刻章|发票代开|刷单|"
                    + "套现|网贷|裸聊|招嫖|约炮|赌博|博彩|毒品|迷药|枪支|弹药|"
                    + "假钞|假币|色情|淫秽|盗版|侵权|翻墙|VPN|梯子|黑客|"
                    + "代发论文|代写论文|代做毕设|代做毕业|"
                    + "香烟|电子烟|烟草|卖烟|买烟|啤酒|白酒|洋酒|卖酒|买酒|处方药|安眠药|"
                    + "管制刀具|匕首|电棍|警用|窃听器|针孔摄像|作弊器|"
                    + "校园贷|高利贷|裸条|传销|直销|拉人头)"
    );
    private static final Pattern PREMIUM_DEVICE = Pattern.compile(
            "(?i)(iphone|ipad|macbook|airpods|apple\\s*watch|苹果手机|苹果平板|switch|ps[45]|相机)"
    );
    private static final Pattern LIKE_NEW = Pattern.compile(
            "(?i)(全新|未拆封|未使用|99新|九九新|95新|九成新|成色好|正常使用|仅用|只用|用了?[一二三四五六七八九十0-9]+个?月)"
    );
    private static final Pattern LOW_VALUE_EXCEPTION = Pattern.compile(
            "(?i)(保护壳|手机壳|平板壳|键盘膜|贴膜|数据线|充电线|充电器|支架|配件|维修|模型|玩具|空盒|包装盒|零件|坏机|故障|损坏|碎屏|屏裂|进水|不开机|报废|拆机|仅供维修|尸体机)"
    );

    private final JsonMapper objectMapper;
    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public AiReviewService(JsonMapper objectMapper,
                           @Value("${zhiyi.ai.api-url:https://api.deepseek.com}") String apiUrl,
                           @Value("${zhiyi.ai.api-key:}") String apiKey,
                           @Value("${zhiyi.ai.model:deepseek-v4-pro}") String model,
                           @Value("${zhiyi.ai.timeout:5000}") long timeoutMillis) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.endpoint = stripTrailingSlash(apiUrl) + "/chat/completions";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(Math.max(500, timeoutMillis));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public ReviewResult review(PublishItemDTO dto, Category category) {
        ReviewResult deterministicViolation = detectPriceAnomaly(dto, category);
        if (deterministicViolation != null) {
            return deterministicViolation;
        }
        ReviewResult keywordViolation = detectContentViolation(dto);
        if (keywordViolation != null) {
            return keywordViolation;
        }
        if (!StringUtils.hasText(apiKey)) {
            log.warn("AI 审核未配置 API Key，跳过远程审核");
            return ReviewResult.degraded("AI 审核未配置，已转人工复核");
        }

        log.info("AI 审核开始: model={}, title={}", model, dto.getTitle());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", buildUserPrompt(dto, category))
        ));
        request.put("response_format", Map.of("type", "json_object"));
        request.put("thinking", Map.of("type", "disabled"));
        request.put("temperature", 0.1);
        request.put("max_tokens", 500);

        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("DeepSeek 内容审核失败: model={}, error={}", model, e.toString());
            return ReviewResult.degraded("AI 审核服务暂不可用，已转人工复核");
        }
    }

    ReviewResult detectPriceAnomaly(PublishItemDTO dto, Category category) {
        if (!"SELL".equalsIgnoreCase(dto.getType()) || dto.getPrice() == null) {
            return null;
        }
        String text = dto.getTitle() + " " + dto.getDescription();
        if (LOW_VALUE_EXCEPTION.matcher(text).find()) {
            return null;
        }

        boolean premiumDeviceAtImpossiblePrice = PREMIUM_DEVICE.matcher(text).find()
                && dto.getPrice().compareTo(PREMIUM_DEVICE_FLOOR) <= 0;
        boolean nearlyNewDigitalAtTokenPrice = "数码电子".equals(category.getName())
                && LIKE_NEW.matcher(text).find()
                && dto.getPrice().compareTo(NEW_DIGITAL_FLOOR) <= 0;
        if (!premiumDeviceAtImpossiblePrice && !nearlyNewDigitalAtTokenPrice) {
            return null;
        }

        String reason = "商品价格 ¥" + dto.getPrice().setScale(2)
                + " 与标题、型号或成色明显不符，疑似虚假价格或交易欺诈";
        return new ReviewResult(true, reason, List.of(), false);
    }

    /**
     * 本地违规关键词检测，不依赖 AI API。
     * 命中则直接拦截，避免 AI Key 未配置时违规内容漏过。
     */
    ReviewResult detectContentViolation(PublishItemDTO dto) {
        String text = dto.getTitle() + " " + dto.getDescription();
        java.util.regex.Matcher matcher = VIOLATION_KEYWORDS.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String matched = matcher.group();
        return new ReviewResult(true, "内容涉嫌违规，包含禁止发布的关键词：" + matched, List.of(), false);
    }

    ReviewResult parseResponse(JsonNode response) {
        JsonNode contentNode = response == null ? null : response.at("/choices/0/message/content");
        if (contentNode == null || !contentNode.isString() || !StringUtils.hasText(contentNode.asString())) {
            throw new IllegalArgumentException("AI 返回内容为空");
        }

        String content = stripCodeFence(contentNode.asString().trim());
        try {
            JsonNode payload = objectMapper.readTree(content);
            JsonNode violationNode = payload.get("is_violation");
            JsonNode tagsNode = payload.get("tags");
            if (violationNode == null || !violationNode.isBoolean() || tagsNode == null || !tagsNode.isArray()) {
                throw new IllegalArgumentException("AI 返回 JSON 缺少必要字段");
            }

            Set<String> tags = new LinkedHashSet<>();
            tagsNode.forEach(node -> {
                if (node.isString()) {
                    String tag = node.asString().trim();
                    if (StringUtils.hasText(tag) && tag.length() <= 20 && tags.size() < 6) {
                        tags.add(tag);
                    }
                }
            });
            if (tags.isEmpty() && !violationNode.asBoolean()) {
                throw new IllegalArgumentException("AI 未返回有效标签");
            }

            String reason = payload.path("violation_reason").asString("").trim();
            if (violationNode.asBoolean() && !StringUtils.hasText(reason)) {
                reason = "内容不符合校园交易发布规范";
            }
            return new ReviewResult(violationNode.asBoolean(), reason, List.copyOf(tags), false);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 返回格式无法解析", e);
        }
    }

    private String buildUserPrompt(PublishItemDTO dto, Category category) {
        return "发布类型：" + dto.getType()
                + "\n所属大类：" + category.getName()
                + "\n标题：" + dto.getTitle()
                + "\n描述：" + dto.getDescription()
                + "\n价格：" + dto.getPrice()
                + "\n交易地点：" + dto.getTradeLocation();
    }

    private String stripCodeFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLine = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstLine < 0 || lastFence <= firstLine) {
            return content;
        }
        return content.substring(firstLine + 1, lastFence).trim();
    }

    private static String stripTrailingSlash(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://api.deepseek.com";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record ReviewResult(boolean violation, String reason, List<String> tags, boolean reviewError) {
        static ReviewResult degraded(String reason) {
            return new ReviewResult(false, reason, List.of(), true);
        }
    }
}
