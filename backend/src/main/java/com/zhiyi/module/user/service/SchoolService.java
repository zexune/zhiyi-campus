package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.vo.SchoolVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 模块一创新功能：学校字典（A1/A9）
 * 注册/个人资料的学校下拉、学校校验与邮箱后缀匹配均从这里取数。
 */
@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolMapper schoolMapper;

    /** 全部启用中的学校（注册页和个人资料下拉，公开接口） */
    public List<SchoolVO> listActiveSchools() {
        return schoolMapper.selectList(Wrappers.<School>lambdaQuery()
                        .eq(School::getStatus, "ACTIVE")
                        .orderByAsc(School::getId))
                .stream()
                .map(SchoolVO::from)
                .toList();
    }

    /** 按 ID 取启用中的学校；不存在或已停用返回 null（注册和资料更新校验用） */
    public School getActiveSchool(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        School school = schoolMapper.selectById(schoolId);
        if (school == null || !"ACTIVE".equals(school.getStatus())) {
            return null;
        }
        return school;
    }

    /**
     * 取学校名称用于展示（个人信息、公开名片）。
     * 与 {@link #getActiveSchool} 不同：这里只需名字，停用学校也照常回显历史归属，查不到返回 null。
     */
    public String schoolNameOf(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        School school = schoolMapper.selectById(schoolId);
        return school == null ? null : school.getName();
    }

    /**
     * 归一化并校验学校邮箱。
     * 邮箱为空时返回 null；非空时必须存在有效学校，且域名与学校配置完全匹配。
     */
    public String normalizeAndValidateEmail(String rawEmail, School school) {
        if (rawEmail == null) {
            return null;
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            return null;
        }
        if (school == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先选择有效的学校");
        }

        String configuredDomain = school.getEmailDomain();
        if (configuredDomain == null || configuredDomain.isBlank()) {
            return email;
        }
        String domain = configuredDomain.trim().toLowerCase(Locale.ROOT);
        int at = email.lastIndexOf('@');
        String actualDomain = at < 0 ? "" : email.substring(at);
        if (!actualDomain.equals(domain)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "邮箱须使用 " + school.getName() + " 的邮箱后缀 " + configuredDomain);
        }
        return email;
    }
}
