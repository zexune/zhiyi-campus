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
 * - 可观测性：两类拒绝分开计数并节流告警（第 1 次与此后每第 100 次）——
 *   全局容量超限是节点负载信号（值得运维关注）；同商品单飞碰撞是正常业务互斥，
 *   仅高频出现时才提示热点商品；
 * - 正确性完全由数据库状态机、行锁与唯一约束保证；未来多实例部署时，
 *   每实例闸门保护本节点连接池，全局正确性仍由数据库承担。
 */
@Slf4j
@Component
public class TradingAdmissionGate {

    private final Semaphore globalSlots;
    private final int globalCapacity;
    private final long globalAcquireTimeoutMillis;
    private final ConcurrentHashMap<Long, Object> inFlightItems = new ConcurrentHashMap<>();
    private final AtomicInteger singleFlightRejects = new AtomicInteger();
    private final AtomicInteger globalBusyRejects = new AtomicInteger();

    public TradingAdmissionGate(
            @Value("${zhiyi.trade.admission-global-slots:30}") int globalSlots,
            @Value("${zhiyi.trade.admission-wait-millis:200}") long globalAcquireTimeoutMillis) {
        this.globalCapacity = Math.max(1, globalSlots);
        this.globalSlots = new Semaphore(globalCapacity);
        this.globalAcquireTimeoutMillis = Math.max(0, globalAcquireTimeoutMillis);
    }

    /**
     * 以 itemId 为单飞键执行交易动作。准入失败直接抛出 TRADE_BUSY，
     * 不获取数据库连接、不创建幂等记录。
     */
    public <T> T withItemAdmission(Long itemId, Supplier<T> action) {
        Object marker = new Object();
        if (inFlightItems.putIfAbsent(itemId, marker) != null) {
            // 节流告警：正常业务互斥，仅高频出现（热点商品）时值得回看
            if (singleFlightRejects.incrementAndGet() % 100 == 1) {
                log.warn("交易同商品单飞拒绝：商品 {} 已有并发交易处理中，累计拒绝 {} 次",
                        itemId, singleFlightRejects.get());
            }
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
                // 节流告警：节点负载信号（与 AuthAdmissionGate 同一口径）
                if (globalBusyRejects.incrementAndGet() % 100 == 1) {
                    log.warn("交易准入背压：并发交易已满 {} 槽位，累计拒绝 {} 次",
                            globalCapacity, globalBusyRejects.get());
                }
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
