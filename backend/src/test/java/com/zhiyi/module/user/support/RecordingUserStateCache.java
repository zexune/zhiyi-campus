package com.zhiyi.module.user.support;

import com.zhiyi.module.user.mapper.SysUserMapper;

import java.util.ArrayList;
import java.util.List;

/** Test double that records which cache invalidation contract a service uses. */
public final class RecordingUserStateCache extends UserStateCache {

    private final List<Long> immediateInvalidations = new ArrayList<>();
    private final List<Long> afterCommitInvalidations = new ArrayList<>();

    public RecordingUserStateCache(SysUserMapper userMapper) {
        super(userMapper, 60);
    }

    @Override
    public void invalidate(Long userId) {
        immediateInvalidations.add(userId);
    }

    @Override
    public void invalidateAfterCommit(Long userId) {
        afterCommitInvalidations.add(userId);
    }

    public List<Long> immediateInvalidations() {
        return List.copyOf(immediateInvalidations);
    }

    public List<Long> afterCommitInvalidations() {
        return List.copyOf(afterCommitInvalidations);
    }
}
