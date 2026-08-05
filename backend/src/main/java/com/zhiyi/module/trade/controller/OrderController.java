package com.zhiyi.module.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.Result;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.dto.ReviewDTO;
import com.zhiyi.module.trade.service.OrderService;
import com.zhiyi.module.trade.service.ReviewService;
import com.zhiyi.module.trade.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 模块四 · 担保交易接口
 *
 * POST /api/order/create         创建订单（购买）
 * PUT  /api/order/{id}/confirm   确认收货
 * PUT  /api/order/{id}/cancel    取消订单
 * GET  /api/order/my-bought      我买的
 * GET  /api/order/my-sold        我卖的
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;

    @PostMapping("/create")
    public Result<OrderVO> create(@RequestAttribute("userId") Long userId,
                                  @Valid @RequestBody CreateOrderDTO dto) {
        return Result.ok("下单成功，资金已冻结", orderService.createOrder(userId, dto));
    }

    @PutMapping("/{id}/confirm")
    public Result<OrderVO> confirm(@RequestAttribute("userId") Long userId,
                                   @PathVariable Long id) {
        return Result.ok("收货确认成功", orderService.confirmReceipt(id, userId));
    }

    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@RequestAttribute("userId") Long userId,
                                  @PathVariable Long id) {
        return Result.ok("订单已取消，退款已到账", orderService.cancelOrder(id, userId));
    }

    @GetMapping("/my-bought")
    public Result<IPage<OrderVO>> myBought(@RequestAttribute("userId") Long userId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String status) {
        return Result.ok(orderService.getBoughtOrders(userId, page, size, status));
    }

    @GetMapping("/my-sold")
    public Result<IPage<OrderVO>> mySold(@RequestAttribute("userId") Long userId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String status) {
        return Result.ok(orderService.getSoldOrders(userId, page, size, status));
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
