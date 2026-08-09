package com.zhiyi.module.item.service;

import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
        assertEquals("2026.1", result.ruleVersion());
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

    private PublishItemDTO request(String text, BigDecimal price) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType("SELL");
        dto.setTitle(text);
        dto.setDescription("正常描述");
        dto.setPrice(price);
        return dto;
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("数码电子");
        return category;
    }
}
