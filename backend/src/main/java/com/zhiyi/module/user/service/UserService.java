package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.module.user.dto.UpdateProfileDTO;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import com.zhiyi.module.user.vo.PublicUserCardVO;
import com.zhiyi.module.user.vo.SellerDetailVO;
import com.zhiyi.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 模块一：个人信息与经验值记录
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final ExpLogMapper expLogMapper;
    private final SchoolService schoolService;

    /** 当前用户信息 */
    public UserVO getProfile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return UserVO.from(user, schoolService.schoolNameOf(user.getSchoolId()));
    }

    /**
     * 更新学校、学校邮箱、昵称/手机号与校区等信任资料。
     * 学校允许因转学、升学等情况修改；学校邮箱无需验证码，但必须匹配目标学校后缀。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        boolean schoolProvided = dto.getSchoolId() != null;
        boolean emailProvided = dto.getSchoolEmail() != null;
        boolean campusProvided = dto.getCampus() != null;
        boolean collegeProvided = dto.getCollege() != null;
        boolean gradeProvided = dto.getGrade() != null;
        boolean dormitoryProvided = dto.getDormitory() != null;
        String targetEmail = current.getSchoolEmail();
        String targetCampus = campusProvided ? blankToNull(dto.getCampus()) : current.getCampus();
        String targetCollege = collegeProvided ? blankToNull(dto.getCollege()) : current.getCollege();
        String targetGrade = gradeProvided ? blankToNull(dto.getGrade()) : current.getGrade();
        String targetDormitory = dormitoryProvided ? blankToNull(dto.getDormitory()) : current.getDormitory();
        if (schoolProvided || emailProvided) {
            Long targetSchoolId = schoolProvided ? dto.getSchoolId() : current.getSchoolId();
            School targetSchool = schoolService.getActiveSchool(targetSchoolId);
            if (targetSchool == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请选择有效的学校");
            }
            String rawEmail = emailProvided ? dto.getSchoolEmail() : current.getSchoolEmail();
            targetEmail = schoolService.normalizeAndValidateEmail(rawEmail, targetSchool);
        }

        SysUser patch = new SysUser();
        patch.setId(userId);
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            patch.setNickname(dto.getNickname().trim());
        }
        if (dto.getPhone() != null) {
            patch.setPhone(dto.getPhone());
        }
        if (schoolProvided) {
            patch.setSchoolId(dto.getSchoolId());
        }
        if (emailProvided && targetEmail != null) {
            patch.setSchoolEmail(targetEmail);
        }

        // 传了才更新；空串归一化为 null，等价于「清空该项」，不参与信任标签比对
        if (campusProvided && targetCampus != null) {
            patch.setCampus(targetCampus);
        }
        if (collegeProvided && targetCollege != null) {
            patch.setCollege(targetCollege);
        }
        if (gradeProvided && targetGrade != null) {
            patch.setGrade(targetGrade);
        }
        if (dormitoryProvided && targetDormitory != null) {
            patch.setDormitory(targetDormitory);
        }

        // MyBatis-Plus 默认忽略实体中的 null；显式 SET 才能真正清空可选资料。
        var update = Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId, userId);
        if (emailProvided && targetEmail == null) {
            update.set(SysUser::getSchoolEmail, null);
        }
        if (campusProvided && targetCampus == null) {
            update.set(SysUser::getCampus, null);
        }
        if (collegeProvided && targetCollege == null) {
            update.set(SysUser::getCollege, null);
        }
        if (gradeProvided && targetGrade == null) {
            update.set(SysUser::getGrade, null);
        }
        if (dormitoryProvided && targetDormitory == null) {
            update.set(SysUser::getDormitory, null);
        }
        int affected = userMapper.update(patch, update);
        if (affected == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return getProfile(userId);
    }

    /**
     * 伪熟人信任标签（A5）：比对访客与目标的学院/年级/校区/宿舍楼。
     * 关键约束：仅当双方该字段都非空才比对；只返回关系文案，不暴露具体值。
     * 看自己不生成标签。
     */
    public List<String> getRelationTags(Long viewerId, Long targetId) {
        if (viewerId == null || viewerId.equals(targetId)) {
            return List.of();
        }
        SysUser viewer = userMapper.selectById(viewerId);
        SysUser target = userMapper.selectById(targetId);
        if (viewer == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (target == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 校园关系只在同一学校内成立，避免不同学校同名字段产生伪关系。
        if (viewer.getSchoolId() == null || !Objects.equals(viewer.getSchoolId(), target.getSchoolId())) {
            return List.of();
        }
        List<String> tags = new ArrayList<>(4);
        if (bothMatch(viewer.getCollege(), target.getCollege())) {
            tags.add("同学院");
        }
        if (bothMatch(viewer.getGrade(), target.getGrade())) {
            tags.add("同级");
        }
        if (bothMatch(viewer.getCampus(), target.getCampus())) {
            tags.add("同校区");
        }
        if (bothMatch(viewer.getDormitory(), target.getDormitory())) {
            tags.add("同楼");
        }
        return tags;
    }

    /** 两侧均非空白且相等（NULL 字段不参与比对） */
    private boolean bothMatch(String a, String b) {
        return a != null && !a.isBlank() && a.equals(b);
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 经验值变动记录（分页，倒序） */
    public IPage<ExpLog> getExpLogs(Long userId, int page, int size) {
        return expLogMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<ExpLog>lambdaQuery()
                        .eq(ExpLog::getUserId, userId)
                        .orderByDesc(ExpLog::getId));
    }

    /**
     * 公开的用户名片（A8：昵称+等级+学校，商品详情/聊天头像旁展示用，供 B/C 模块调用）。
     */
    public PublicUserCardVO getPublicProfile(Long userId) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getNickname, SysUser::getLevel,
                        SysUser::getSchoolId)
                .eq(SysUser::getId, userId));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return new PublicUserCardVO(
                user.getId(), user.getNickname(), user.getLevel(), LevelRule.titleOf(user.getLevel()),
                schoolService.schoolNameOf(user.getSchoolId()));
    }

    /**
     * 商品详情中的卖家档案。接口需要登录，只返回用户主动填写的联系与校园资料。
     */
    public SellerDetailVO getSellerDetail(Long viewerId, Long userId) {
        SysUser viewer = userMapper.selectById(viewerId);
        if (viewer == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getNickname, SysUser::getLevel,
                        SysUser::getSchoolId, SysUser::getPhone, SysUser::getSchoolEmail,
                        SysUser::getCampus, SysUser::getCollege,
                        SysUser::getGrade, SysUser::getDormitory)
                .eq(SysUser::getId, userId));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SchoolScopeGuard.requireSame(
                viewer.getSchoolId(), user.getSchoolId(), "只能查看本校卖家资料");
        return new SellerDetailVO(
                user.getId(),
                user.getNickname(),
                user.getLevel(),
                LevelRule.titleOf(user.getLevel()),
                schoolService.schoolNameOf(user.getSchoolId()),
                user.getPhone(),
                user.getSchoolEmail(),
                user.getCampus(),
                user.getCollege(),
                user.getGrade(),
                user.getDormitory());
    }
}
