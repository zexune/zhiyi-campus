package com.zhiyi.module.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.dto.ReviewDTO;
import com.zhiyi.module.trade.service.OrderQueryService;
import com.zhiyi.module.trade.service.ReviewService;
import com.zhiyi.module.trade.service.TradingEntryService;
import com.zhiyi.module.trade.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

/**
 * 模块四 · 担保交易接口
 *
 * POST /api/order/create         创建订单（购买）
 * PUT  /api/order/{id}/confirm   确认收货
 * PUT  /api/order/{id}/cancel    取消订单
 * GET  /api/order/my-bought      我买的
 * GET  /api/order/my-sold        我卖的
 *
 * 三个资金操作必须携带 X-Idempotency-Key（UUID）请求头：客户端为每次资金意图
 * 生成并持久化唯一键，网络重试/超时后复用原键取回结果，防止重复扣款/退款。
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    /** 幂等键请求头：36 位 UUID。 */
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final TradingEntryService tradingEntryService;
    private final OrderQueryService orderQueryService;
    private final ReviewService reviewService;

    @PostMapping("/create")
    public Result<OrderVO> create(@RequestAttribute("userId") Long userId,
                                  @Valid @RequestBody CreateOrderDTO dto,
                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        return Result.ok("下单成功，资金已冻结",
                tradingEntryService.createOrder(userId, dto, normalizeKey(idempotencyKey)));
    }

    @PutMapping("/{id}/confirm")
    public Result<OrderVO> confirm(@RequestAttribute("userId") Long userId,
                                   @PathVariable Long id,
                                   @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        return Result.ok("收货确认成功",
                tradingEntryService.confirmReceipt(id, userId, normalizeKey(idempotencyKey)));
    }

    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@RequestAttribute("userId") Long userId,
                                  @PathVariable Long id,
                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        return Result.ok("订单已取消，退款已到账",
                tradingEntryService.cancelOrder(id, userId, normalizeKey(idempotencyKey)));
    }

    /** 校验幂等键格式（36 位 UUID）；缺失或非法时引导客户端刷新页面获取新版本。 */
    private String normalizeKey(String rawKey) {
        String key = rawKey == null ? null : rawKey.trim();
        if (key == null || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(ResultCode.IDEMPOTENCY_KEY_INVALID);
        }
        return key.toLowerCase();
    }

    @GetMapping("/my-bought")
    public Result<IPage<OrderVO>> myBought(@RequestAttribute("userId") Long userId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String status) {
        return Result.ok(orderQueryService.getBoughtOrders(userId, page, size, status));
    }

    @GetMapping("/my-sold")
    public Result<IPage<OrderVO>> mySold(@RequestAttribute("userId") Long userId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String status) {
        return Result.ok(orderQueryService.getSoldOrders(userId, page, size, status));
    }

    /** 买家确认收货后对卖家评价（A7） */
    @PostMapping("/{id}/review")
    public Result<Void> review(@RequestAttribute("userId") Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody ReviewDTO dto) {
        reviewService.review(id, userId, dto);
        return Result.ok("评价成功", null);
    }
}
