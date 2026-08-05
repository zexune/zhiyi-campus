package com.zhiyi.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    // MyBatis-Plus 自动提供 insert / delete / update / selectById / selectList 等
    // 复杂查询（如联合查询）可在此添加方法，并在 mapper/SysUserMapper.xml 中写 SQL

    /**
     * 原子增减经验值（高并发安全：单条 UPDATE 在 DB 端完成 read-modify-write，
     * 避免「读出 exp → Java 加 → 写回」在并发确认收货时互相覆盖丢经验）。
     * GREATEST(0, ...) 保证违规扣分不会扣成负数。
     *
     * @return 受影响行数
     */
    @Update("UPDATE sys_user SET exp = GREATEST(0, exp + #{delta}) WHERE id = #{userId}")
    int incrExp(@Param("userId") Long userId, @Param("delta") int delta);

    /** 原子增减经验后回读最新成长状态（同一事务内，供单向等级结算与流水记录用） */
    @Select("SELECT id, exp, level FROM sys_user WHERE id = #{userId}")
    SysUser selectGrowthState(@Param("userId") Long userId);

    /** 原子推进 Token 版本，使此前签发的所有 JWT 失效。 */
    @Update("UPDATE sys_user SET token_version = token_version + 1 WHERE id = #{userId}")
    int bumpTokenVersion(@Param("userId") Long userId);
}
