package com.zhiyi.module.trade.support;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 交易事务外有界准入闸门（负载保护，不承担正确性职责）。
 *
 * - 同商品单飞：同一商品的并发下单请求只放行一个进入数据库，其余立即返回 TRADE_BUSY；
 * - 全局并发上限：进入交易事务的数量受信号量限制（容量须小于连接池并预留非交易请求空间），
 *   等待预算到期即失败，不允许请求占着数据库连接排队；
 * - 正确性完全由数据库状态机、行锁与唯一约束保证；未来多实例部署时，
 *   每实例闸门保护本节点连接池，全局正确性仍由数据库承担。
 */
@Slf4j
@Component
public class TradingAdmissionGate {

    private final Semaphore globalSlots;
    private final long globalAcquireTimeoutMillis;
    private final ConcurrentHashMap<Long, Object> inFlightItems = new ConcurrentHashMap<>();
    private final AtomicInteger busyRejects = new AtomicInteger();

    public TradingAdmissionGate(
            @Value("${zhiyi.trade.admission-global-slots:30}") int globalSlots,
            @Value("${zhiyi.trade.admission-wait-millis:200}") long globalAcquireTimeoutMillis) {
        this.globalSlots = new Semaphore(Math.max(1, globalSlots));
        this.globalAcquireTimeoutMillis = Math.max(0, globalAcquireTimeoutMillis);
    }

    /**
     * 以 itemId 为单飞键执行交易动作。准入失败直接抛出 TRADE_BUSY，
     * 不获取数据库连接、不创建幂等记录。
     */
    public <T> T withItemAdmission(Long itemId, Supplier<T> action) {
        Object marker = new Object();
        if (inFlightItems.putIfAbsent(itemId, marker) != null) {
            busyRejects.incrementAndGet();
            throw new BusinessException(ResultCode.TRADE_BUSY, "该商品交易请求处理中，请稍后重试")
                    .withRequestOutcome(ResultCode.RequestOutcome.UNKNOWN);
        }
        try {
            return withGlobalAdmission(action);
        } finally {
            inFlightItems.remove(itemId, marker);
        }
    }

    /**
     * 全局容量准入（确认/取消等非同商品单飞路径）。
     *
     * 准入失败只能证明当前物理请求未执行，无法排除同一幂等键的另一请求仍在处理，
     * 因此 outcome 必须保持 UNKNOWN，客户端不得据此清除幂等键。
     */
    public <T> T withGlobalAdmission(Supplier<T> action) {
        boolean acquired = false;
        try {
            try {
                acquired = globalSlots.tryAcquire(globalAcquireTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.TRADE_BUSY)
                        .withRequestOutcome(ResultCode.RequestOutcome.UNKNOWN);
            }
            if (!acquired) {
                busyRejects.incrementAndGet();
                throw new BusinessException(ResultCode.TRADE_BUSY)
                        .withRequestOutcome(ResultCode.RequestOutcome.UNKNOWN);
            }
            return action.get();
        } finally {
            if (acquired) {
                globalSlots.release();
            }
        }
    }
}
