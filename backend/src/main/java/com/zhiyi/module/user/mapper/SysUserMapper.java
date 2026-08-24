package com.zhiyi.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    /** 当前读锁定用户行（登录/状态迁移/交易锁序；REPEATABLE READ 下普通 SELECT 可能读快照）。 */
    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    SysUser selectByIdForUpdate(@Param("id") Long id);

    /** NOWAIT 锁定用户行：锁繁忙立即失败（errno 3572），由调用方映射为可重试背压。 */
    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE NOWAIT")
    SysUser selectByIdForUpdateNowait(@Param("id") Long id);

    /** 鉴权主库直读：只取拦截器所需字段，普通读取不加锁。 */
    @Select("SELECT id, role, status, ban_until_time, token_version, is_system FROM sys_user WHERE id = #{id}")
    SysUser selectAuthState(@Param("id") Long id);

    /** 唯一 SYSTEM 技术主体（系统消息发送者；启动巡检保证恰好一个）。 */
    @Select("SELECT * FROM sys_user WHERE is_system = 1")
    SysUser selectSystemUser();

    /** 全部人工管理员（role=ADMIN 且非 SYSTEM）；由调用方校验恰好一个，禁止 LIMIT 1 掩盖配置异常。 */
    @Select("SELECT * FROM sys_user WHERE role = 'ADMIN' AND is_system = 0")
    List<SysUser> selectHumanAdmins();
}
