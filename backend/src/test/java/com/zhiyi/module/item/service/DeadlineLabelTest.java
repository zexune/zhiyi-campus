package com.zhiyi.module.item.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class DeadlineLabelTest {
    @Test
    void mapsDeadlineToExpectedUrgencyLabel() {
        assertNull(MarketplaceService.deadlineLabel(null));
        assertNull(MarketplaceService.deadlineLabel(LocalDateTime.now().plusDays(8)));
        assertEquals("⏰", MarketplaceService.deadlineLabel(LocalDateTime.now().plusDays(5)));
        assertEquals("⚠️", MarketplaceService.deadlineLabel(LocalDateTime.now().plusDays(2)));
    }
}
