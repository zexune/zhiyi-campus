package com.zhiyi.module.item.service;

import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 完全本地、确定性的内容分析器：只做文本规则匹配与普通标签提取，不评价价格。
 */
@Service
public class LocalContentAnalyzer {

    private static final List<Rule> RULES = List.of(
            new Rule("ACADEMIC_MISCONDUCT", "学术不端",
                    List.of("代写", "代考", "代课", "代签到", "替考", "作弊", "代发论文", "代写论文", "代做毕设", "代做毕业")),
            new Rule("FORGED_DOCUMENT", "伪造证件或票据",
                    List.of("办假证", "办证", "刻章", "发票代开", "假钞", "假币")),
            new Rule("FINANCIAL_RISK", "非法金融活动",
                    List.of("刷单", "套现", "网贷", "校园贷", "高利贷", "裸条")),
            new Rule("SEXUAL_CONTENT", "色情或招嫖信息",
                    List.of("裸聊", "招嫖", "约炮", "色情", "淫秽")),
            new Rule("GAMBLING_DRUGS_WEAPONS", "赌博、毒品或危险器具",
                    List.of("赌博", "博彩", "毒品", "迷药", "枪支", "弹药", "管制刀具", "匕首", "电棍")),
            new Rule("REGULATED_GOODS", "烟酒、处方药等限制商品",
                    List.of("香烟", "电子烟", "烟草", "卖烟", "买烟", "啤酒", "白酒", "洋酒", "卖酒", "买酒", "处方药", "安眠药")),
            new Rule("PRIVACY_OR_CHEATING_DEVICE", "窃听、偷拍或作弊设备",
                    List.of("警用", "窃听器", "针孔摄像", "作弊器")),
            new Rule("COPYRIGHT_OR_BYPASS", "侵权或网络绕过服务",
                    List.of("盗版", "侵权", "翻墙", "vpn", "梯子", "黑客")),
            new Rule("PYRAMID_SCHEME", "传销或拉人头",
                    List.of("传销", "直销", "拉人头"))
    );

    private static final List<String> KNOWN_TAGS = List.of(
            "iPad", "苹果", "小米", "华为", "耳机", "键盘", "充电宝", "教材", "高数", "考研",
            "四级", "全新", "99新", "有笔记", "台灯", "风扇", "背包", "运动鞋", "篮球", "Switch"
    );

    @Value("${zhiyi.moderation.rule-version:2026.1}")
    private String ruleVersion = "2026.1";

    public AnalysisResult analyze(PublishItemDTO dto, Category category) {
        String visibleText = String.join(" ", safe(dto.getTitle()), safe(dto.getDescription()),
                safe(dto.getTradeLocation()), safe(dto.getPickupLocation()), safe(dto.getDeliveryLocation()));
        String normalized = normalizeForMatching(visibleText);

        List<String> matchedCodes = new ArrayList<>();
        List<String> matchedLabels = new ArrayList<>();
        for (Rule rule : RULES) {
            String matchedKeyword = rule.keywords().stream()
                    .filter(keyword -> normalized.contains(normalizeForMatching(keyword)))
                    .findFirst()
                    .orElse(null);
            if (matchedKeyword != null) {
                matchedCodes.add(rule.code());
                matchedLabels.add(rule.label() + "（" + matchedKeyword + "）");
            }
        }

        List<String> tags = generateTags(dto, category, visibleText);
        String reason = matchedLabels.isEmpty()
                ? ""
                : "本地规则检测到可能违规内容：" + String.join("、", matchedLabels);
        return new AnalysisResult(!matchedCodes.isEmpty(), reason,
                List.copyOf(matchedCodes), ruleVersion, tags);
    }

    String normalizeForMatching(String value) {
        String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private List<String> generateTags(PublishItemDTO dto, Category category, String text) {
        Set<String> tags = new LinkedHashSet<>();
        if (category != null && StringUtils.hasText(category.getName())) {
            tags.add(category.getName().trim());
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String candidate : KNOWN_TAGS) {
            if (lower.contains(candidate.toLowerCase(Locale.ROOT)) && tags.size() < 6) {
                tags.add(candidate);
            }
        }
        for (String token : text.split("[\\s,，。.!！?？、/\\\\()（）\\[\\]【】]+")) {
            String value = token.trim();
            if (value.length() >= 2 && value.length() <= 16 && tags.size() < 5) {
                tags.add(value);
            }
        }
        tags.add(switch (dto.getType()) {
            case "SELL" -> "出售";
            case "BUY" -> "求购";
            case "SWAP" -> "以物换物";
            case "ERRAND" -> "校园跑腿";
            default -> dto.getType();
        });
        return tags.stream().limit(6).toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record Rule(String code, String label, List<String> keywords) {
    }

    public record AnalysisResult(boolean risky,
                                 String reason,
                                 List<String> matchedRules,
                                 String ruleVersion,
                                 List<String> tags) {
    }
}
