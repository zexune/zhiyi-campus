package com.zhiyi.module.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.Result;
import com.zhiyi.module.trade.dto.RechargeDTO;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.service.TradingEntryService;
import com.zhiyi.module.trade.service.WalletService;
import com.zhiyi.module.trade.vo.WalletBalanceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 模块四 · 钱包接口
 *
 * GET   /api/wallet/balance   查询余额
 * POST  /api/wallet/recharge  模拟充值（需 X-Idempotency-Key 幂等键，防重复充值）
 * GET   /api/wallet/logs      资金流水（分页）
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final TradingEntryService tradingEntryService;
    private final WalletService walletService;

    @GetMapping("/balance")
    public Result<WalletBalanceVO> balance(@RequestAttribute("userId") Long userId) {
        return Result.ok(walletService.getBalance(userId));
    }

    @PostMapping("/recharge")
    public Result<WalletBalanceVO> recharge(@RequestAttribute("userId") Long userId,
                                            @Valid @RequestBody RechargeDTO dto,
                                            @RequestHeader(OrderController.IDEMPOTENCY_HEADER) String idempotencyKey) {
        return Result.ok("充值成功",
                tradingEntryService.recharge(userId, dto.getAmount(), idempotencyKey.trim().toLowerCase()));
    }

    @GetMapping("/logs")
    public Result<IPage<WalletLog>> logs(@RequestAttribute("userId") Long userId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return Result.ok(walletService.getLogs(userId, page, size));
    }
}
