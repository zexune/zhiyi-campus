package com.zhiyi.module.user.support;

import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserStateCacheTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(SysUser.class, SysUserMapper.class);
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
        assertEquals(UserStatus.ACTIVE, fixture.cache().get(1L).status());
        fixture.database().set(user("BANNED_PERM"));

        fixture.cache().invalidateAfterCommit(1L);

        assertEquals(UserStatus.BANNED_PERM, fixture.cache().get(1L).status());
    }

    @Test
    void waitsUntilCommitBeforeInvalidating() {
        Fixture fixture = fixture("ACTIVE");
        fixture.cache().get(1L);
        fixture.database().set(user("BANNED_PERM"));
        beginSynchronization();

        fixture.cache().invalidateAfterCommit(1L);
        assertEquals(UserStatus.ACTIVE, fixture.cache().get(1L).status());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertEquals(UserStatus.BANNED_PERM, fixture.cache().get(1L).status());
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

        assertEquals(UserStatus.ACTIVE, fixture.cache().get(1L).status());
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
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.valueOf(status));
        user.setTokenVersion(0);
        return user;
    }

    private record Fixture(
            UserStateCache cache,
            AtomicReference<SysUser> database) {
    }
}
