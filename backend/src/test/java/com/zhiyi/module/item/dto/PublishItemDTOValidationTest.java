package com.zhiyi.module.item.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishItemDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsOneCharacterTitle() {
        PublishItemDTO dto = validDTO();
        dto.setTitle("A");

        long titleViolations = validator.validate(dto).stream()
                .filter(v -> "title".equals(v.getPropertyPath().toString()))
                .count();

        assertEquals(1, titleViolations);
    }

    @Test
    void allowsOneCharacterDescription() {
        PublishItemDTO dto = validDTO();
        dto.setDescription("A");

        long descriptionViolations = validator.validate(dto).stream()
                .filter(v -> "description".equals(v.getPropertyPath().toString()))
                .count();

        assertEquals(0, descriptionViolations);
    }

    @Test
    void rejectsBlankDescription() {
        PublishItemDTO dto = validDTO();
        dto.setDescription("   ");

        long descriptionViolations = validator.validate(dto).stream()
                .filter(v -> "description".equals(v.getPropertyPath().toString()))
                .count();

        assertEquals(1, descriptionViolations);
    }

    @Test
    void allowsSwapOnlyWhenPriceIsNull() {
        PublishItemDTO dto = validDTO();
        dto.setType("SWAP");
        dto.setPrice(null);
        assertEquals(0, validator.validate(dto).size());

        dto.setPrice(BigDecimal.ONE);
        assertEquals(1, validator.validate(dto).stream().filter(v -> "typeDetailsValid".equals(v.getPropertyPath().toString())).count());
    }

    @Test
    void validatesErrandRewardRouteAndDeadline() {
        PublishItemDTO dto = validDTO();
        dto.setType("ERRAND");
        dto.setPrice(new BigDecimal("5.00"));
        dto.setPickupLocation("南门快递站");
        dto.setDeliveryLocation("3号宿舍楼");
        dto.setDeadlineTime(LocalDateTime.now().plusHours(2));
        assertEquals(0, validator.validate(dto).size());

        dto.setPrice(new BigDecimal("21.00"));
        assertEquals(1, validator.validate(dto).stream().filter(v -> "typeDetailsValid".equals(v.getPropertyPath().toString())).count());
    }

    private PublishItemDTO validDTO() {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType("SELL");
        dto.setTitle("Used textbook");
        dto.setDescription("A well-maintained textbook for sale.");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("10.00"));
        dto.setImages(List.of("/uploads/items/20260722/test.jpg"));
        dto.setTradeLocation("Library entrance");
        return dto;
    }
}
