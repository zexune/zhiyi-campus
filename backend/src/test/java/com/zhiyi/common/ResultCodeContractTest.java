package com.zhiyi.common;

import com.zhiyi.common.ResultCode.RequestOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1-2 契约检查：错误来源 → HTTP 状态唯一映射、业务码唯一登记、
 * 未知码无静默兜底、幂等处置与 Retry-After 语义。
 */
class ResultCodeContractTest {

    @Test
    @DisplayName("契约不变量：业务码唯一、HTTP 状态绑定完整、成功码唯一")
    void contractInvariantsHold() {
        ResultCode.assertContractInvariants();
    }

    @Test
    @DisplayName("认证矩阵固化：USER_CANCELLED 是业务 403，SESSION_INVALIDATED 是认证 401")
    void authErrorSemanticsArePinned() {
        assertEquals(403, ResultCode.USER_CANCELLED.getHttpStatus().value());
        assertEquals(1008, ResultCode.USER_CANCELLED.getCode());
        assertEquals(401, ResultCode.SESSION_INVALIDATED.getHttpStatus().value());
        assertEquals(1401, ResultCode.SESSION_INVALIDATED.getCode());
        assertEquals(401, ResultCode.UNAUTHORIZED.getHttpStatus().value());
        // 凭证错误必须是业务 400/429，绝不能借用 401（401 会触发前端登出）
        assertEquals(400, ResultCode.PASSWORD_ERROR.getHttpStatus().value());
        assertEquals(429, ResultCode.LOGIN_LOCKED.getHttpStatus().value());
    }

    @Test
    @DisplayName("未登记业务码：登记检查失败而非静默映射 400")
    void unknownCodeHasNoSilentFallback() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ResultCode.of(9999));
        assertEquals("未登记的业务码：9999——请在 ResultCode 显式登记并绑定 HTTP 状态", error.getMessage());
    }

    @Test
    @DisplayName("幂等处置：明确拒绝、处理中与结果不明具有不同默认分类")
    void requestOutcomesAreClassified() {
        assertEquals(RequestOutcome.REJECTED, ResultCode.BALANCE_NOT_ENOUGH.requestOutcome());
        assertEquals(RequestOutcome.REJECTED, ResultCode.USER_CANCELLED.requestOutcome());
        assertEquals(RequestOutcome.REJECTED, ResultCode.IDEMPOTENCY_CONFLICT.requestOutcome());
        assertEquals(RequestOutcome.UNKNOWN, ResultCode.TRADE_BUSY.requestOutcome());
        assertEquals(RequestOutcome.PROCESSING, ResultCode.IDEMPOTENCY_PROCESSING.requestOutcome());
        assertEquals(RequestOutcome.UNKNOWN, ResultCode.SERVER_ERROR.requestOutcome());
    }

    @Test
    @DisplayName("退避重试只有允许等待的失败才携带 Retry-After（LOGIN_LOCKED 静态值仅兜底，运行时用实例覆盖）")
    void retryAfterOnlyWhereBackoffIsAllowed() {
        assertEquals(2, ResultCode.TRADE_BUSY.getRetryAfterSeconds());
        assertEquals(3, ResultCode.IDEMPOTENCY_PROCESSING.getRetryAfterSeconds());
        assertEquals(300, ResultCode.LOGIN_LOCKED.getRetryAfterSeconds());
        assertEquals(0, ResultCode.SERVER_ERROR.getRetryAfterSeconds());
        assertEquals(0, ResultCode.BALANCE_NOT_ENOUGH.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("成功信封不可能携带非 200 成功码；失败信封不可能携带 200")
    void envelopeInvariantsAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApiSuccess<Void>(401, "不可能", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ApiFailure(200, "不可能", null, null));
    }
}
