package com.zhiyi.module.user.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserStateCacheTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.user.mapper.SysUserMapper");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void invalidatesImmediatelyWithoutTransaction() {
        Fixture fixture = fixture("ACTIVE");
        assertEquals("ACTIVE", fixture.cache().get(1L).getStatus());
        fixture.database().set(user("BANNED_PERM"));

        fixture.cache().invalidateAfterCommit(1L);

        assertEquals("BANNED_PERM", fixture.cache().get(1L).getStatus());
    }

    @Test
    void waitsUntilCommitBeforeInvalidating() {
        Fixture fixture = fixture("ACTIVE");
        fixture.cache().get(1L);
        fixture.database().set(user("BANNED_PERM"));
        beginSynchronization();

        fixture.cache().invalidateAfterCommit(1L);
        assertEquals("ACTIVE", fixture.cache().get(1L).getStatus());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertEquals("BANNED_PERM", fixture.cache().get(1L).getStatus());
    }

    @Test
    void rollbackKeepsExistingCacheEntry() {
        Fixture fixture = fixture("ACTIVE");
        fixture.cache().get(1L);
        fixture.database().set(user("BANNED_PERM"));
        beginSynchronization();

        fixture.cache().invalidateAfterCommit(1L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        assertEquals("ACTIVE", fixture.cache().get(1L).getStatus());
    }

    private void beginSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private Fixture fixture(String status) {
        AtomicReference<SysUser> database =
                new AtomicReference<>(user(status));
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectOne(any())).thenAnswer(ignored -> database.get());
        return new Fixture(new UserStateCache(mapper, 60), database);
    }

    private SysUser user(String status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRole("USER");
        user.setStatus(status);
        user.setTokenVersion(0);
        return user;
    }

    private record Fixture(
            UserStateCache cache,
            AtomicReference<SysUser> database) {
    }
}
