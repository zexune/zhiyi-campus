package com.zhiyi.common.support;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.entity.IdempotencyRecord;
import com.zhiyi.module.trade.mapper.IdempotencyRecordMapper;
import com.zhiyi.module.trade.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等协议（owner-token）单元测试：覆盖 §4.7 的所有权判定分支——
 * 执行权获取、SUCCESS 快照复返、同键不同参数冲突、处理中、键格式校验。
 * 不依赖驱动 affected rows 语义（插入一律走 DuplicateKey/成功两态模拟）。
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String KEY = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Mock private IdempotencyRecordMapper recordMapper;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(recordMapper);
    }

    private IdempotencyRecord record(String ownerToken, String requestHash, String status, String snapshot) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(7L);
        record.setOperation(IdempotencyService.OP_ORDER_CREATE);
        record.setIdempotencyKey(KEY);
        record.setRequestHash(requestHash);
        record.setOwnerToken(ownerToken);
        record.setStatus(status);
        record.setResultSnapshot(snapshot);
        return record;
    }

    @Test
    void freshKeyAcquiresOwnershipExecutesAndMarksSuccess() {
        // 模拟本事务插入成功：FOR UPDATE 重读返回本次写入的记录（token 与本次一致 → 执行权）
        java.util.concurrent.atomic.AtomicReference<IdempotencyRecord> insertedRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(recordMapper.insert(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            insertedRef.set(invocation.getArgument(0));
            return 1;
        });
        when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                .thenAnswer(invocation -> insertedRef.get());
        when(recordMapper.markSuccess(eq(7L), eq(IdempotencyService.OP_ORDER_CREATE), eq(KEY),
                anyString(), anyString())).thenReturn(1);
        OrderVO result = new OrderVO();
        AtomicInteger executed = new AtomicInteger();

        OrderVO returned = service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                java.util.Map.of("itemId", 9), OrderVO.class, () -> {
                    executed.incrementAndGet();
                    return result;
                });

        assertSame(result, returned);
        assertEquals(1, executed.get());
        // 记录携带随机 owner_token 与规范化请求哈希，状态 PROCESSING
        ArgumentCaptor<IdempotencyRecord> insertedCaptor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(recordMapper).insert(insertedCaptor.capture());
        IdempotencyRecord inserted = insertedCaptor.getValue();
        assertEquals(36, inserted.getOwnerToken().length());
        assertEquals("PROCESSING", inserted.getStatus());
        verify(recordMapper).markSuccess(eq(7L), eq(IdempotencyService.OP_ORDER_CREATE), eq(KEY),
                eq(inserted.getOwnerToken()), anyString());
    }

    @Test
    void duplicateRequestReturnsPersistedSuccessSnapshot() {
        // 第一阶段：让本事务"插入成功"以捕获实际写入的 requestHash 与 token
        java.util.concurrent.atomic.AtomicReference<IdempotencyRecord> insertedRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(recordMapper.insert(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            insertedRef.set(invocation.getArgument(0));
            return 1;
        });
        when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                .thenAnswer(invocation -> insertedRef.get());
        when(recordMapper.markSuccess(any(), any(), any(), any(), any())).thenReturn(1);
        service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                java.util.Map.of("itemId", 9), OrderVO.class, OrderVO::new);

        // 第二阶段：同键重复请求（旧 owner 已 SUCCESS）→ 复返持久化快照，不执行业务
        when(recordMapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        String snapshot = "{\"id\":41,\"status\":\"WAITING_MEET\"}";
        IdempotencyRecord committed = insertedRef.get();
        when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                .thenReturn(record(committed.getOwnerToken(), committed.getRequestHash(), "SUCCESS", snapshot));

        OrderVO returned = service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                java.util.Map.of("itemId", 9), OrderVO.class, () -> {
                    throw new AssertionError("复返路径不得执行业务函数");
                });

        assertEquals(41L, returned.getId());
        assertEquals("WAITING_MEET", returned.getStatus());
        // 第二阶段不再提交 SUCCESS（复返不重复落库）
        verify(recordMapper).markSuccess(any(), any(), any(), any(), any());
    }

    @Test
    void sameKeyWithDifferentParamsIsRejectedAsConflict() {
        when(recordMapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        // 原请求哈希与本次不同 → 参数冲突
        when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                .thenReturn(record("old-owner-token", "different-hash", "SUCCESS", "{}"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                        java.util.Map.of("itemId", 999), OrderVO.class, () -> new OrderVO()));

        assertEquals(ResultCode.IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void processingRecordReturnsRetainableResultPending() {
        // 捕获同参数下的真实哈希，保证 PROCESSING 分支可被命中（而非先触发参数冲突）
        java.util.concurrent.atomic.AtomicReference<IdempotencyRecord> insertedRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(recordMapper.insert(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            insertedRef.set(invocation.getArgument(0));
            return 1;
        });
        when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                .thenAnswer(invocation -> insertedRef.get());
        when(recordMapper.markSuccess(any(), any(), any(), any(), any())).thenReturn(1);
        service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                java.util.Map.of("itemId", 9), OrderVO.class, OrderVO::new);

        when(recordMapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        IdempotencyRecord committed = insertedRef.get();
        when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                .thenReturn(record(committed.getOwnerToken(), committed.getRequestHash(), "PROCESSING", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                        java.util.Map.of("itemId", 9), OrderVO.class, () -> new OrderVO()));

        assertEquals(ResultCode.IDEMPOTENCY_PROCESSING.getCode(), error.getCode());
    }

    @Test
    void malformedKeyIsRejectedBeforeAnyDatabaseAccess() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.execute(7L, IdempotencyService.OP_ORDER_CREATE, "not-an-uuid",
                        java.util.Map.of(), OrderVO.class, () -> new OrderVO()));

        assertEquals(ResultCode.IDEMPOTENCY_KEY_INVALID.getCode(), error.getCode());
        verify(recordMapper, never()).insert(any(IdempotencyRecord.class));
    }

    @Test
    void successUpdateMustAffectExactlyOneRow() {
        when(recordMapper.insert(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord inserted = invocation.getArgument(0);
            when(recordMapper.selectByUniqueForUpdate(7L, IdempotencyService.OP_ORDER_CREATE, KEY))
                    .thenReturn(inserted);
            return 1;
        });
        when(recordMapper.markSuccess(any(), any(), any(), any(), any())).thenReturn(0); // 异常竞争

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.execute(7L, IdempotencyService.OP_ORDER_CREATE, KEY,
                        java.util.Map.of("itemId", 9), OrderVO.class, () -> new OrderVO()));

        assertEquals(ResultCode.SERVER_ERROR.getCode(), error.getCode());
    }
}
