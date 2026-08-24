package com.zhiyi.module.trade.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.support.TradingAdmissionGate;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.trade.vo.WalletBalanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 交易入口编排（不启事务）：交易准入通过后再调用受 Spring 代理的事务 Bean。
 *
 * 闸门只负责负载保护——同商品单飞 + 全局并发上限，等待预算到期即返回可重试的
 * TRADE_BUSY，此时不获取数据库连接、不创建幂等记录；
 * 交易正确性仍完全由数据库状态机、行锁和唯一约束保证。
 *
 * 死锁重试兜底：@RetryOnDeadlock 耗尽后不再依赖 @Recover（spring-retry 的
 * recover 方法匹配存在版本怪癖），耗尽的 ConcurrencyFailureException 在本层
 * 统一转 TRADE_BUSY（幂等 RETAIN，客户端保留原键退避重试）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingEntryService {

    private final TradingAdmissionGate admissionGate;
    private final OrderService orderService;
    private final WalletService walletService;

    public OrderVO createOrder(Long buyerId, CreateOrderDTO dto, String idempotencyKey) {
        return admissionGate.withItemAdmission(dto.getItemId(),
                () -> translateLockExhaustion("createOrder",
                        () -> orderService.createOrder(buyerId, dto, idempotencyKey)));
    }

    public OrderVO confirmReceipt(Long orderId, Long buyerId, String idempotencyKey) {
        return admissionGate.withGlobalAdmission(
                () -> translateLockExhaustion("confirmReceipt",
                        () -> orderService.confirmReceipt(orderId, buyerId, idempotencyKey)));
    }

    public OrderVO cancelOrder(Long orderId, Long buyerId, String idempotencyKey) {
        return admissionGate.withGlobalAdmission(
                () -> translateLockExhaustion("cancelOrder",
                        () -> orderService.cancelOrder(orderId, buyerId, idempotencyKey)));
    }

    public WalletBalanceVO recharge(Long userId, BigDecimal amount, String idempotencyKey) {
        return admissionGate.withGlobalAdmission(
                () -> translateLockExhaustion("recharge",
                        () -> walletService.recharge(userId, amount, idempotencyKey)));
    }

    private <T> T translateLockExhaustion(String operation, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (ConcurrencyFailureException exhausted) {
            log.warn("资金事务死锁重试耗尽 operation={}", operation, exhausted);
            throw new BusinessException(ResultCode.TRADE_BUSY);
        }
    }
}
