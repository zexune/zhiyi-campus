package com.zhiyi.module.item.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReviewServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiReviewService service = new AiReviewService(
            objectMapper, "https://api.deepseek.com", "test-key", "deepseek-v4-pro", 5000);

    @Test
    void parsesCompliantJsonOutputAndDeduplicatesTags() throws Exception {
        var response = objectMapper.readTree("""
                {"choices":[{"message":{"content":"{\\"is_violation\\":false,\\"violation_reason\\":\\"\\",\\"tags\\":[\\"iPad\\",\\"苹果\\",\\"iPad\\"]}"}}]}
                """);

        AiReviewService.ReviewResult result = service.parseResponse(response);

        assertFalse(result.violation());
        assertFalse(result.reviewError());
        assertEquals(List.of("iPad", "苹果"), result.tags());
    }

    @Test
    void parsesViolationReasonFromFencedJson() throws Exception {
        var response = objectMapper.readTree("""
                {"choices":[{"message":{"content":"```json\\n{\\"is_violation\\":true,\\"violation_reason\\":\\"涉及代考服务\\",\\"tags\\":[]}\\n```"}}]}
                """);

        AiReviewService.ReviewResult result = service.parseResponse(response);

        assertTrue(result.violation());
        assertEquals("涉及代考服务", result.reason());
    }

    @Test
    void blocksOneYuanNearlyNewIpadBeforeCallingAi() {
        AiReviewService localService = new AiReviewService(
                objectMapper, "https://api.deepseek.com", "", "deepseek-v4-pro", 5000);
        PublishItemDTO dto = item("99 新 iPad Air5", "只用了六个月，考研后出", "1.00");

        AiReviewService.ReviewResult result = localService.review(dto, digitalCategory());

        assertTrue(result.violation());
        assertFalse(result.reviewError());
        assertTrue(result.reason().contains("疑似虚假价格"));
    }

    @Test
    void allowsCheapIpadAccessoryToContinueToAiReview() {
        AiReviewService localService = new AiReviewService(
                objectMapper, "https://api.deepseek.com", "", "deepseek-v4-pro", 5000);
        PublishItemDTO dto = item("iPad Air5 保护壳", "普通透明保护壳，闲置出", "1.00");

        AiReviewService.ReviewResult result = localService.review(dto, digitalCategory());

        assertFalse(result.violation());
        assertTrue(result.reviewError());
    }

    private PublishItemDTO item(String title, String description, String price) {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType("SELL");
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setPrice(new BigDecimal(price));
        dto.setTradeLocation("图书馆门口");
        return dto;
    }

    private Category digitalCategory() {
        Category category = new Category();
        category.setName("数码电子");
        return category;
    }
}
