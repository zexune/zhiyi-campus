package com.zhiyi.module.trade.controller;

import com.zhiyi.common.ApiHeaders;
import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResponse;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.dto.ReviewDTO;
import com.zhiyi.module.trade.service.OrderQueryService;
import com.zhiyi.module.trade.service.ReviewService;
import com.zhiyi.module.trade.service.TradingEntryService;
import com.zhiyi.module.trade.vo.OrderDetailResponse;
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

    /** 幂等键请求头：36 位 UUID；常量单一来源见 {@link ApiHeaders}。 */
    public static final String IDEMPOTENCY_HEADER = ApiHeaders.IDEMPOTENCY_KEY;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final TradingEntryService tradingEntryService;
    private final OrderQueryService orderQueryService;
    private final ReviewService reviewService;

    @PostMapping("/create")
    @BusinessErrors({ResultCode.BALANCE_NOT_ENOUGH, ResultCode.ITEM_NOT_ON_SALE,
            ResultCode.ORDER_STATUS_ERROR, ResultCode.USER_STATUS_ERROR, ResultCode.TRADE_BUSY,
            ResultCode.IDEMPOTENCY_CONFLICT, ResultCode.IDEMPOTENCY_PROCESSING,
            ResultCode.IDEMPOTENCY_KEY_INVALID, ResultCode.NOT_FOUND, ResultCode.FORBIDDEN,
            ResultCode.CONFLICT, ResultCode.SERVER_ERROR})
    public ApiSuccess<OrderDetailResponse> create(@RequestAttribute("userId") Long userId,
                                                  @Valid @RequestBody CreateOrderDTO dto,
                                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        return ApiSuccess.ok("下单成功，资金已冻结",
                OrderDetailResponse.from(tradingEntryService.createOrder(userId, dto, normalizeKey(idempotencyKey))));
    }

    @PutMapping("/{id}/confirm")
    @BusinessErrors({ResultCode.ORDER_STATUS_ERROR, ResultCode.TRADE_BUSY,
            ResultCode.IDEMPOTENCY_CONFLICT, ResultCode.IDEMPOTENCY_PROCESSING,
            ResultCode.IDEMPOTENCY_KEY_INVALID, ResultCode.NOT_FOUND, ResultCode.FORBIDDEN,
            ResultCode.CONFLICT, ResultCode.USER_STATUS_ERROR, ResultCode.USER_NOT_FOUND,
            ResultCode.SERVER_ERROR})
    public ApiSuccess<OrderDetailResponse> confirm(@RequestAttribute("userId") Long userId,
                                                   @PathVariable Long id,
                                                   @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        return ApiSuccess.ok("收货确认成功",
                OrderDetailResponse.from(tradingEntryService.confirmReceipt(id, userId, normalizeKey(idempotencyKey))));
    }

    @PutMapping("/{id}/cancel")
    @BusinessErrors({ResultCode.ORDER_STATUS_ERROR, ResultCode.TRADE_BUSY,
            ResultCode.IDEMPOTENCY_CONFLICT, ResultCode.IDEMPOTENCY_PROCESSING,
            ResultCode.IDEMPOTENCY_KEY_INVALID, ResultCode.NOT_FOUND, ResultCode.FORBIDDEN,
            ResultCode.CONFLICT, ResultCode.USER_STATUS_ERROR, ResultCode.SERVER_ERROR})
    public ApiSuccess<OrderDetailResponse> cancel(@RequestAttribute("userId") Long userId,
                                                  @PathVariable Long id,
                                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        return ApiSuccess.ok("订单已取消，退款已到账",
                OrderDetailResponse.from(tradingEntryService.cancelOrder(id, userId, normalizeKey(idempotencyKey))));
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
    @BusinessErrors
    public ApiSuccess<PageResponse<OrderVO>> myBought(@RequestAttribute("userId") Long userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(required = false) String status) {
        return ApiSuccess.ok(PageResponse.from(orderQueryService.getBoughtOrders(userId, page, size, status)));
    }

    @GetMapping("/my-sold")
    @BusinessErrors
    public ApiSuccess<PageResponse<OrderVO>> mySold(@RequestAttribute("userId") Long userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) String status) {
        return ApiSuccess.ok(PageResponse.from(orderQueryService.getSoldOrders(userId, page, size, status)));
    }

    /** 买家确认收货后对卖家评价（A7） */
    @PostMapping("/{id}/review")
    @BusinessErrors({ResultCode.ORDER_STATUS_ERROR, ResultCode.ORDER_ALREADY_REVIEWED,
            ResultCode.NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> review(@RequestAttribute("userId") Long userId,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ReviewDTO dto) {
        reviewService.review(id, userId, dto);
        return ApiSuccess.ok("评价成功", null);
    }
}
