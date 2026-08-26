package com.zhiyi.module.trade.support;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingAdmissionGateTest {

    @Test
    void sameItemCollisionIsUnknownRatherThanClaimingSameRequestIsProcessing() throws Exception {
        TradingAdmissionGate gate = new TradingAdmissionGate(2, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = executor.submit(() -> gate.withItemAdmission(9L, () -> {
                entered.countDown();
                await(release);
                return "ok";
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> gate.withItemAdmission(9L, () -> "unexpected"));

            assertEquals(ResultCode.TRADE_BUSY, error.getResultCode());
            assertEquals(ResultCode.RequestOutcome.UNKNOWN, error.effectiveRequestOutcome());
            release.countDown();
            assertEquals("ok", first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void globalCapacityRejectionIsUnknownBecauseSameKeyRequestMayStillBeRunning() throws Exception {
        TradingAdmissionGate gate = new TradingAdmissionGate(1, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = executor.submit(() -> gate.withGlobalAdmission(() -> {
                entered.countDown();
                await(release);
                return "ok";
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> gate.withGlobalAdmission(() -> "unexpected"));

            assertEquals(ResultCode.TRADE_BUSY, error.getResultCode());
            assertEquals(ResultCode.RequestOutcome.UNKNOWN, error.effectiveRequestOutcome());
            release.countDown();
            assertEquals("ok", first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedAdmissionIsUnknownBecauseIdempotencyOwnershipWasNotChecked() {
        TradingAdmissionGate gate = new TradingAdmissionGate(1, 100);
        Thread.currentThread().interrupt();
        try {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> gate.withGlobalAdmission(() -> "unexpected"));

            assertEquals(ResultCode.TRADE_BUSY, error.getResultCode());
            assertEquals(ResultCode.RequestOutcome.UNKNOWN, error.effectiveRequestOutcome());
        } finally {
            Thread.interrupted();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("测试线程被意外中断", interrupted);
        }
    }
}
