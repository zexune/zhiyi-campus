package com.zhiyi.module.user.support;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private MutableTicker ticker;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        ticker = new MutableTicker();
        service = new LoginAttemptService(5, 300, ticker);
    }

    @Test
    void equivalentSpellingsShareOneCounter() {
        service.recordFailure("Admin");
        service.recordFailure(" admin ");
        service.recordFailure("ADMIN");
        service.recordFailure("AdMiN");
        service.recordFailure("admin");

        assertTrue(service.isLocked(" ADMIN "));
    }

    @Test
    void lastFailureRefreshesTheFullWindow() {
        service.recordFailure("admin");
        ticker.advance(Duration.ofSeconds(299));
        for (int i = 0; i < 4; i++) {
            service.recordFailure("admin");
        }

        ticker.advance(Duration.ofSeconds(2));
        assertTrue(service.isLocked("admin"));
        ticker.advance(Duration.ofSeconds(299));
        assertFalse(service.isLocked("admin"));
    }

    @Test
    void resetClearsEquivalentSpellings() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("ADMIN");
        }

        service.reset(" admin ");

        assertFalse(service.isLocked("Admin"));
    }

    private static final class MutableTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
