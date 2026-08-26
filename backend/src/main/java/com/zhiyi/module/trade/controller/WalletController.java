package com.zhiyi.module.trade.controller;

import com.zhiyi.common.ApiHeaders;
import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.PageResponse;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.module.trade.dto.RechargeDTO;
import com.zhiyi.module.trade.service.TradingEntryService;
import com.zhiyi.module.trade.service.WalletService;
import com.zhiyi.module.trade.vo.WalletBalanceVO;
import com.zhiyi.module.trade.vo.WalletLogResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<WalletBalanceVO> balance(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(walletService.getBalance(userId));
    }

    @PostMapping("/recharge")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.USER_STATUS_ERROR, ResultCode.TRADE_BUSY,
            ResultCode.IDEMPOTENCY_CONFLICT, ResultCode.IDEMPOTENCY_PROCESSING,
            ResultCode.IDEMPOTENCY_KEY_INVALID, ResultCode.SERVER_ERROR})
    public ApiSuccess<WalletBalanceVO> recharge(@RequestAttribute("userId") Long userId,
                                                @Valid @RequestBody RechargeDTO dto,
                                                @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String idempotencyKey) {
        return ApiSuccess.ok("充值成功",
                tradingEntryService.recharge(userId, dto.getAmount(), idempotencyKey.trim().toLowerCase()));
    }

    @GetMapping("/logs")
    @BusinessErrors
    public ApiSuccess<PageResponse<WalletLogResponse>> logs(@RequestAttribute("userId") Long userId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return ApiSuccess.ok(PageResponse.from(
                walletService.getLogs(userId, page, size).convert(WalletLogResponse::from)));
    }
}
