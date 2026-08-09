package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.SchoolStatus;
import com.zhiyi.module.admin.dto.SchoolDTO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.vo.SchoolVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 超管学校管理服务 —— D1
 *
 * GET    /api/admin/schools     列表（含停用）
 * POST   /api/admin/schools     新增
 * PUT    /api/admin/schools/{id} 编辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSchoolService {

    private final SchoolMapper schoolMapper;
    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;

    /**
     * 学校列表。status 可选：传 "ACTIVE" 仅返回启用学校（供大盘下拉等场景），不传则返回全部（含停用/已删除）。
     */
    public List<SchoolVO> listAll(String status) {
        var q = Wrappers.<School>lambdaQuery().orderByAsc(School::getId);
        if (status != null && !status.isBlank()) {
            q.eq(School::getStatus, parseStatus(status));
        }
        return schoolMapper.selectList(q).stream().map(SchoolVO::from).toList();
    }

    /** 新增学校 */
    @Transactional(rollbackFor = Exception.class)
    public SchoolVO create(SchoolDTO dto) {
        // code 唯一性校验
        School existing = schoolMapper.selectOne(Wrappers.<School>lambdaQuery()
                .eq(School::getCode, dto.getCode()));
        if (existing != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学校代码 " + dto.getCode() + " 已存在");
        }

        School school = new School();
        school.setName(dto.getName());
        school.setCode(dto.getCode().toUpperCase());
        school.setEmailDomain(dto.getEmailDomain());
        school.setStatus(dto.getStatus() != null ? parseStatus(dto.getStatus()) : SchoolStatus.ACTIVE);
        schoolMapper.insert(school);

        log.info("管理员新增学校：{} ({})", school.getName(), school.getCode());
        return SchoolVO.from(school);
    }

    /** 编辑学校 */
    @Transactional(rollbackFor = Exception.class)
    public SchoolVO update(Long id, SchoolDTO dto) {
        School school = schoolMapper.selectById(id);
        if (school == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学校不存在");
        }

        // code 唯一性校验（排除自身）
        School dup = schoolMapper.selectOne(Wrappers.<School>lambdaQuery()
                .eq(School::getCode, dto.getCode())
                .ne(School::getId, id));
        if (dup != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学校代码 " + dto.getCode() + " 已被其他学校使用");
        }

        school.setName(dto.getName());
        school.setCode(dto.getCode().toUpperCase());
        school.setEmailDomain(dto.getEmailDomain());
        if (dto.getStatus() != null) {
            school.setStatus(parseStatus(dto.getStatus()));
        }
        schoolMapper.updateById(school);

        log.info("管理员编辑学校：{} ({})", school.getName(), school.getCode());
        return SchoolVO.from(school);
    }

    /**
     * 删除空学校。存在用户或商品关联时只允许停用，避免悬空外键。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        School school = schoolMapper.selectById(id);
        if (school == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学校不存在");
        }
        // 检查依赖：有用户或商品归属时拒绝删除，引导走 DISABLED
        boolean hasUsers = sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getSchoolId, id)) > 0;
        if (hasUsers) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该学校下仍有用户，无法删除。如需停用请将状态设为 DISABLED");
        }
        boolean hasItems = itemMapper.selectCount(
                Wrappers.<Item>lambdaQuery()
                        .eq(Item::getSchoolId, id)) > 0;
        if (hasItems) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该学校下仍有商品记录，无法删除。如需停用请将状态设为 DISABLED");
        }

        schoolMapper.deleteById(id);
        log.info("管理员删除空学校：{} ({})", school.getName(), school.getCode());
    }

    private SchoolStatus parseStatus(String value) {
        try {
            return SchoolStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException invalidStatus) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学校状态仅支持 ACTIVE 或 DISABLED");
        }
    }
}
