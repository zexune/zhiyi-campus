package com.zhiyi.module.user.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.PageResponse;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.module.user.dto.CancelAccountDTO;
import com.zhiyi.module.user.dto.ChangePasswordDTO;
import com.zhiyi.module.user.dto.UpdateProfileDTO;
import com.zhiyi.module.user.service.AccountSecurityService;
import com.zhiyi.module.user.service.ReputationService;
import com.zhiyi.module.user.service.UserService;
import com.zhiyi.module.user.vo.ExpLogResponse;
import com.zhiyi.module.user.vo.PublicUserCardVO;
import com.zhiyi.module.user.vo.ReputationVO;
import com.zhiyi.module.user.vo.SellerDetailVO;
import com.zhiyi.module.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模块一：用户信息与成长体系接口（B.1 附录接口清单）
 *
 * GET  /api/user/profile           当前用户信息
 * PUT  /api/user/profile           更新个人信息
 * GET  /api/user/exp-log           经验值变动记录
 * GET  /api/user/{id}/card         公开名片（昵称+等级，供商品详情/聊天展示）
 * PUT  /api/user/change-password   修改密码（验证原密码，新旧不得相同）
 * POST /api/user/cancel-account    注销账号（软注销，密码确认）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountSecurityService accountSecurityService;
    private final ReputationService reputationService;

    @GetMapping("/profile")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<UserVO> profile(@RequestAttribute("userId") Long userId) {
        return ApiSuccess.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.PROFILE_CONFLICT})
    public ApiSuccess<UserVO> updateProfile(@RequestAttribute("userId") Long userId,
                                            @Valid @RequestBody UpdateProfileDTO dto) {
        return ApiSuccess.ok("保存成功", userService.updateProfile(userId, dto));
    }

    @GetMapping("/exp-log")
    @BusinessErrors
    public ApiSuccess<PageResponse<ExpLogResponse>> expLog(@RequestAttribute("userId") Long userId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return ApiSuccess.ok(PageResponse.from(
                userService.getExpLogs(userId, page, size).convert(ExpLogResponse::from)));
    }

    @GetMapping("/{id}/card")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<PublicUserCardVO> card(@PathVariable Long id) {
        return ApiSuccess.ok(userService.getPublicProfile(id));
    }

    /** 商品详情卖家档案：含联系方式和校园资料，仅登录用户可查看。 */
    @GetMapping("/{id}/seller-detail")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<SellerDetailVO> sellerDetail(@RequestAttribute("userId") Long viewerId,
                                                   @PathVariable Long id) {
        return ApiSuccess.ok(userService.getSellerDetail(viewerId, id));
    }

    /** 伪熟人信任标签（A5）：当前登录用户视角看目标用户 → ["同学院","同级","同校区","同楼"] */
    @GetMapping("/{id}/relation")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<List<String>> relation(@RequestAttribute("userId") Long viewerId,
                                             @PathVariable Long id) {
        return ApiSuccess.ok(userService.getRelationTags(viewerId, id));
    }

    /** 信誉雷达六维分值（A6）：公开接口，供个人中心和卖家详情的雷达图渲染 */
    @GetMapping("/{id}/reputation")
    @BusinessErrors(ResultCode.USER_NOT_FOUND)
    public ApiSuccess<ReputationVO> reputation(@PathVariable Long id) {
        return ApiSuccess.ok(reputationService.compute(id));
    }

    @PutMapping("/change-password")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.PASSWORD_ERROR, ResultCode.LOGIN_LOCKED,
            ResultCode.SAME_AS_OLD_PASSWORD})
    public ApiSuccess<Void> changePassword(@RequestAttribute("userId") Long userId,
                                           @Valid @RequestBody ChangePasswordDTO dto) {
        accountSecurityService.changePassword(userId, dto);
        return ApiSuccess.ok("密码修改成功，请重新登录", null);
    }

    @PostMapping("/cancel-account")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.PASSWORD_ERROR, ResultCode.FORBIDDEN,
            ResultCode.CONFLICT})
    public ApiSuccess<Void> cancelAccount(@RequestAttribute("userId") Long userId,
                                          @Valid @RequestBody CancelAccountDTO dto) {
        accountSecurityService.cancelAccount(userId, dto);
        return ApiSuccess.ok("账号已注销", null);
    }
}
