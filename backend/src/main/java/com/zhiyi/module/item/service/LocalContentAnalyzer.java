package com.zhiyi.module.item.service;

import com.github.promeg.pinyinhelper.Pinyin;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 完全本地、确定性的内容分析器：关键词经 Aho–Corasick 自动机单趟匹配；
 * 只做文本规则匹配与普通标签提取，不评价价格。
 *
 * 拼音的唯一用途是把汉字关键词展开出全拉丁拼写变体（daixie≈代写）并入同一
 * 模式集——拉丁拼音串在正文里出现本身就是刻意规避，无日常用语碰撞问题。
 * 同音汉字判定（带写≈代写）曾实测误报不可接受后移除：无声调音节空间里
 * 双音节关键词与高频合法用语大面积碰撞（读博≈赌博、菠菜≈博彩、电子眼≈
 * 电子烟、强制≈枪支、悦跑≈约炮等），语义级同音识别属于后续本地小模型的范畴。
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

    /** 用户自定义标签的数量上限 */
    public static final int MAX_USER_TAGS = 6;
    /** 用户自定义标签的单个长度上限 */
    public static final int MAX_TAG_LENGTH = 12;

    /** 一次命中的完整上下文：evidence 为展示给审核员的实际命中文本。 */
    private record KeywordHit(String ruleCode, String ruleLabel, String keyword,
                              boolean pinyinSpelling, String evidence) {
    }

    /** 单一字面域模式集：归一化关键词 + 全拉丁拼音拼写变体。 */
    private static final Trie KEYWORD_TRIE;
    /** 模式串 → 命中信息。 */
    private static final Map<String, KeywordHit> PATTERN_INDEX;

    static {
        Map<String, KeywordHit> patterns = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            for (String keyword : rule.keywords()) {
                String normalizedKeyword = normalizeForMatching(keyword);
                if (!patterns.containsKey(normalizedKeyword)) {
                    patterns.put(normalizedKeyword, new KeywordHit(
                            rule.code(), rule.label(), keyword, false, normalizedKeyword));
                }
                if (containsHan(normalizedKeyword)) {
                    String spelled = latinPinyinSpelling(normalizedKeyword);
                    if (!patterns.containsKey(spelled)) {
                        patterns.put(spelled, new KeywordHit(
                                rule.code(), rule.label(), keyword, true, spelled));
                    }
                }
            }
        }
        PATTERN_INDEX = Map.copyOf(patterns);
        var builder = Trie.builder();
        patterns.keySet().forEach(builder::addKeyword);
        KEYWORD_TRIE = builder.build();
    }

    @Value("${zhiyi.moderation.rule-version:2026.2}")
    private String ruleVersion = "2026.2";

    public AnalysisResult analyze(PublishItemDTO dto, Category category) {
        String visibleText = String.join(" ", safe(dto.getTitle()), safe(dto.getDescription()),
                safe(dto.getTradeLocation()), safe(dto.getPickupLocation()), safe(dto.getDeliveryLocation()));
        String normalized = normalizeForMatching(visibleText);

        RuleMatches matches = matchRules(normalized);

        List<String> tags = generateTags(dto, category, visibleText);
        String reason = matches.labels().isEmpty()
                ? ""
                : "本地规则检测到可能违规内容：" + String.join("、", matches.labels());
        return new AnalysisResult(!matches.codes().isEmpty(), reason,
                List.copyOf(matches.codes()), ruleVersion, tags);
    }

    /**
     * 用户自定义标签清洗：trim、去重（忽略大小写）、限量限长，并对标签文本做违规词匹配。
     * risky 为 true 时 tags 返回空列表——命中违规词的标签不允许进入商品标签体系。
     */
    public TagCheck sanitizeUserTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new TagCheck(List.of(), false, "", List.of());
        }
        Set<String> unique = new LinkedHashSet<>();
        Set<String> seenIgnoreCase = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (raw == null) continue;
            String tag = raw.trim();
            if (tag.length() < 2 || tag.length() > MAX_TAG_LENGTH) continue;
            // 忽略大小写去重，保留首次出现的原始写法（与落库层 tag.normalizedName 同规则口径）
            if (seenIgnoreCase.add(tag.toLowerCase(Locale.ROOT))) {
                unique.add(tag);
            }
            if (unique.size() >= MAX_USER_TAGS) break;
        }
        if (unique.isEmpty()) {
            return new TagCheck(List.of(), false, "", List.of());
        }
        RuleMatches matches = matchRules(normalizeForMatching(String.join(" ", unique)));
        if (!matches.codes().isEmpty()) {
            String reason = "标签包含可能违规内容：" + String.join("、", matches.labels());
            return new TagCheck(List.of(), true, reason, List.copyOf(matches.codes()));
        }
        return new TagCheck(List.copyOf(unique), false, "", List.of());
    }

    /**
     * 标签建议：仅依据标题与分类生成候选，不做任何持久化。
     * 供发布页与专题配置的"可选系统标签"使用；类型维度固定按 SELL 口径，避免建议里混入交易动词。
     */
    public List<String> suggestTags(String title, Category category) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType(ItemType.SELL.code());
        dto.setTitle(title == null ? "" : title);
        dto.setDescription("");
        return generateTags(dto, category, safe(dto.getTitle()));
    }

    /** 归一化文本过单一 Trie；同一规则保留最强证据（字面 > 拼音拼写）。 */
    private RuleMatches matchRules(String normalizedText) {
        Map<String, KeywordHit> bestByRule = new LinkedHashMap<>();
        for (Emit emit : KEYWORD_TRIE.parseText(normalizedText)) {
            offerHit(bestByRule, PATTERN_INDEX.get(emit.getKeyword()));
        }
        List<String> codes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (KeywordHit hit : bestByRule.values()) {
            codes.add(hit.ruleCode());
            labels.add(hit.pinyinSpelling()
                    ? hit.ruleLabel() + "（" + hit.keyword() + "·拼音「" + hit.evidence() + "」）"
                    : hit.ruleLabel() + "（" + hit.keyword() + "）");
        }
        return new RuleMatches(codes, labels);
    }

    private static void offerHit(Map<String, KeywordHit> bestByRule, KeywordHit hit) {
        if (hit == null) {
            return;
        }
        KeywordHit existing = bestByRule.get(hit.ruleCode());
        if (existing == null || (existing.pinyinSpelling() && !hit.pinyinSpelling())) {
            bestByRule.put(hit.ruleCode(), hit);
        }
    }

    static String normalizeForMatching(String value) {
        String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    /** 汉字逐字替换为无声调拼音（多音字取常用读音），其余字符原样保留。 */
    private static String latinPinyinSpelling(String normalizedKeyword) {
        StringBuilder spelled = new StringBuilder(normalizedKeyword.length() * 2);
        for (int i = 0; i < normalizedKeyword.length(); i++) {
            char character = normalizedKeyword.charAt(i);
            spelled.append(Pinyin.isChinese(character)
                    ? Pinyin.toPinyin(character).toLowerCase(Locale.ROOT)
                    : character);
        }
        return spelled.toString();
    }

    private static boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint ->
                codePoint <= 0xFFFF && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
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
        tags.add(switch (ItemType.from(dto.getType())) {
            case SELL -> "出售";
            case BUY -> "求购";
            case SWAP -> "以物换物";
            case ERRAND -> "校园跑腿";
        });
        return tags.stream().limit(6).toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Rule(String code, String label, List<String> keywords) {
    }

    private record RuleMatches(List<String> codes, List<String> labels) {
    }

    public record AnalysisResult(boolean risky,
                                 String reason,
                                 List<String> matchedRules,
                                 String ruleVersion,
                                 List<String> tags) {
    }

    /** 用户自定义标签的清洗结果：risky 时 tags 恒为空列表 */
    public record TagCheck(List<String> tags, boolean risky, String reason, List<String> matchedRules) {
    }
}
