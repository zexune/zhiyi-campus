package com.zhiyi.module.item.service;

import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalContentAnalyzerTest {

    private final LocalContentAnalyzer analyzer = new LocalContentAnalyzer();

    @Test
    void detectsObfuscatedKeywordWithDeterministicRuleCode() {
        PublishItemDTO dto = request("可提供代-写-论-文服务", new BigDecimal("1.00"));

        LocalContentAnalyzer.AnalysisResult result = analyzer.analyze(dto, category());

        assertTrue(result.risky());
        assertEquals("2026.2", result.ruleVersion());
        assertTrue(result.matchedRules().contains("ACADEMIC_MISCONDUCT"));
    }

    @Test
    void normalizesFullWidthLatinCharacters() {
        PublishItemDTO dto = request("出售ＶＰＮ账号", new BigDecimal("20.00"));

        LocalContentAnalyzer.AnalysisResult result = analyzer.analyze(dto, category());

        assertTrue(result.risky());
        assertTrue(result.matchedRules().contains("COPYRIGHT_OR_BYPASS"));
    }

    @Test
    void doesNotUsePriceAsModerationSignal() {
        PublishItemDTO cheap = request("正常使用的平板保护套", new BigDecimal("0.01"));
        PublishItemDTO expensive = request("正常使用的平板保护套", new BigDecimal("999999.99"));

        assertFalse(analyzer.analyze(cheap, category()).risky());
        assertFalse(analyzer.analyze(expensive, category()).risky());
    }

    @Test
    void generatesOrdinarySearchTagsLocally() {
        PublishItemDTO dto = request("99新 iPad 平板", new BigDecimal("2000.00"));

        LocalContentAnalyzer.AnalysisResult result = analyzer.analyze(dto, category());

        assertFalse(result.risky());
        assertTrue(result.tags().contains("数码电子"));
        assertTrue(result.tags().contains("iPad"));
        assertTrue(result.tags().contains("出售"));
    }

    @Test
    @DisplayName("全拉丁拼音拼写（banzheng≈办证）按字面模式命中")
    void detectsFullLatinPinyinSpelling() {
        PublishItemDTO dto = request("低价banzheng秒出", new BigDecimal("100.00"));

        LocalContentAnalyzer.AnalysisResult result = analyzer.analyze(dto, category());

        assertTrue(result.risky());
        assertTrue(result.matchedRules().contains("FORGED_DOCUMENT"));
        assertTrue(result.reason().contains("拼音"));
        assertTrue(result.reason().contains("banzheng"));
    }

    @Test
    @DisplayName("拉丁拼写变体在用户标签里同样被拦截")
    void latinSpellingFlaggedInUserTags() {
        LocalContentAnalyzer.TagCheck check = analyzer.sanitizeUserTags(List.of("daixie论文"));

        assertTrue(check.risky());
        assertTrue(check.tags().isEmpty());
        assertTrue(check.matchedRules().contains("ACADEMIC_MISCONDUCT"));
    }

    @Test
    @DisplayName("字面命中保持原有朴素标注，不附加拼音说明")
    void literalHitKeepsPlainLabel() {
        PublishItemDTO dto = request("代写论文包过", new BigDecimal("50.00"));

        LocalContentAnalyzer.AnalysisResult result = analyzer.analyze(dto, category());

        assertTrue(result.risky());
        assertTrue(result.reason().contains("（代写）"));
        assertFalse(result.reason().contains("拼音"));
    }

    /**
     * 同音汉字判定层移除后的回归基线：这些均为常见校园用语，曾在无声调拼音投影上
     * 与关键词完全同音而误报（实测 11 例，全部导致商品被误下架转审）。
     * 语义级同音识别（带写≈代写）是有意接受的盲区，待本地小模型方案解决。
     */
    @Test
    @DisplayName("常见校园用语不再因同音碰撞误报（同音层移除回归基线）")
    void commonCampusPhrasesNoLongerCollideViaHomophones() {
        List<String> probePhrases = List.of(
                "学长读博经验分享笔记",   // 读博≈赌博
                "食堂今天有菠菜鸡蛋",     // 菠菜≈博彩
                "忘带学生证可以进场吗",   // 忘带≈网贷
                "宿舍但要自己买插排",     // 但要≈弹药
                "这段文字比较隐晦",       // 隐晦≈淫秽
                "学校强制晚自习通知",     // 强制≈枪支
                "找科长盖章流程",         // 科长≈刻章
                "悦跑圈跑量截图",         // 悦跑≈约炮
                "这本小说情节香艳",       // 香艳≈香烟
                "校门口电子眼抓拍",       // 电子眼≈电子烟
                "食堂代客加工饭菜");      // 代客≈代课
        for (String phrase : probePhrases) {
            assertFalse(analyzer.analyze(request(phrase, new BigDecimal("10.00")), category()).risky(),
                    "不应误报：" + phrase);
        }
        // 曾经依赖同音层命中的变体现在放行，这是移除同音层的已知代价
        assertFalse(analyzer.analyze(request("有偿带写毕业论文", new BigDecimal("50.00")), category()).risky());
        // 字面命中不受影响
        assertTrue(analyzer.analyze(request("替考包过", new BigDecimal("200.00")), category()).risky());
    }

    private PublishItemDTO request(String text, BigDecimal price) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType("SELL");
        dto.setTitle(text);
        dto.setDescription("正常描述");
        dto.setPrice(price);
        return dto;
    }

    @Test
    void sanitizesUserTagsWithNormalizationAndCap() {
        LocalContentAnalyzer.TagCheck check = analyzer.sanitizeUserTags(
                List.of(" 95新 ", "95XIN", "", "a", "iPhone", "IPHONE", "可小刀", "可小刀"));

        assertFalse(check.risky());
        // 空白 trim、忽略大小写去重（保留首次出现的原始写法，95新 与 95XIN 是不同字符串不合并）、
        // 过短（<2字）剔除
        assertEquals(List.of("95新", "95XIN", "iPhone", "可小刀"), check.tags());
    }

    @Test
    void capsUserTagsAtSix() {
        LocalContentAnalyzer.TagCheck check = analyzer.sanitizeUserTags(
                List.of("标签一", "标签二", "标签三", "标签四", "标签五", "标签六", "标签七", "标签八"));

        assertEquals(6, check.tags().size());
    }

    @Test
    void flagsRiskyUserTagsAndWithholdsThem() {
        LocalContentAnalyzer.TagCheck check = analyzer.sanitizeUserTags(List.of("代写数学作业"));

        assertTrue(check.risky());
        assertTrue(check.tags().isEmpty());
        assertTrue(check.matchedRules().contains("ACADEMIC_MISCONDUCT"));
        assertTrue(check.reason().contains("标签"));
    }

    @Test
    void nullUserTagsMeansNotProvided() {
        LocalContentAnalyzer.TagCheck check = analyzer.sanitizeUserTags(null);

        assertFalse(check.risky());
        assertTrue(check.tags().isEmpty());
    }

    @Test
    void suggestsTagsFromTitleAndCategoryWithoutPersisting() {
        List<String> suggestions = analyzer.suggestTags("99新 iPad 平板", category());

        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.contains("数码电子"));
        assertTrue(suggestions.contains("iPad"));
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("数码电子");
        return category;
    }
}
