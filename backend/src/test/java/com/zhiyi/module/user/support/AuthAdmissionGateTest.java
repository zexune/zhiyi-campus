package com.zhiyi.module.user.support;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证准入闸门：容量、等待预算与许可归还（异常路径不得泄漏许可）。
 */
class AuthAdmissionGateTest {

    @Test
    void zeroSlotsDerivesTwiceCpuCapacity() {
        // 哨兵 0 = 按 2×CPU 自动推导；显式正数则固定生效
        AuthAdmissionGate derived = new AuthAdmissionGate(0, 0);
        assertEquals(2 * Runtime.getRuntime().availableProcessors(), derived.capacity());
        assertEquals(8, new AuthAdmissionGate(8, 0).capacity());
    }

    @Test
    void admitsWhenSlotsAvailable() {
        AuthAdmissionGate gate = new AuthAdmissionGate(2, 0);

        assertEquals("ok", gate.withAdmission(() -> "ok"));
    }

    @Test
    void saturatedGateFailsFastWithAuthBusy() throws InterruptedException {
        AuthAdmissionGate gate = new AuthAdmissionGate(1, 0);
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread occupant = new Thread(() -> gate.withAdmission(() -> {
            inFlight.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        try {
            occupant.start();
            assertTrue(inFlight.await(5, TimeUnit.SECONDS));

            BusinessException busy = assertThrows(BusinessException.class,
                    () -> gate.withAdmission(() -> "never"));
            assertEquals(ResultCode.AUTH_BUSY.getCode(), busy.getCode());
            assertEquals(ResultCode.RequestOutcome.REJECTED, ResultCode.AUTH_BUSY.requestOutcome());
        } finally {
            release.countDown();
            occupant.join(5000);
        }
    }

    @Test
    void failingActionStillReleasesPermit() {
        AuthAdmissionGate gate = new AuthAdmissionGate(1, 0);

        assertThrows(BusinessException.class, () -> gate.withAdmission(() -> {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }));
        // 许可已归还：同一闸门可继续放行后续请求
        assertEquals("ok", gate.withAdmission(() -> "ok"));
    }

    @Test
    void waitBudgetAllowsShortBurstQueuing() throws InterruptedException {
        // 等待预算 500ms：占位者持许可 100ms，排队请求应等到许可而非 AUTH_BUSY
        AuthAdmissionGate gate = new AuthAdmissionGate(1, 500);
        Thread occupant = new Thread(() -> gate.withAdmission(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        occupant.start();
        Thread.sleep(50);

        assertEquals("ok", gate.withAdmission(() -> "ok"));
        occupant.join(5000);
    }
}
