package com.zhiyi.common.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.entity.IdempotencyRecord;
import com.zhiyi.module.trade.mapper.IdempotencyRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 资金操作幂等协议（owner-token 版）。
 *
 * 协议要点：
 * 0. execute 与调用方事务同物理事务（REQUIRED）：业务失败时幂等记录随业务写入一并回滚，
 *    数据库中不残留可见 PROCESSING。
 * 1. 服务端校验键格式并计算完整规范化请求参数的 SHA-256；客户端不能提供 owner_token。
 * 2. 本次调用生成随机 owner_token 尝试插入；无论驱动 affected rows 语义如何，
 *    一律继续 FOR UPDATE 锁定并重读唯一键记录。
 * 3. token 相同 → 本事务新建记录并获得执行权；token 不同 → 旧请求占有该键：
 *    hash 不同为参数冲突；SUCCESS 复返持久化结果；其余为处理中。
 * 4. 业务操作、流水、结果快照与 SUCCESS 状态同事务提交；SUCCESS 更新校验 token 且影响 1 行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String OP_RECHARGE = "RECHARGE";
    public static final String OP_ORDER_CREATE = "ORDER_CREATE";
    public static final String OP_ORDER_CONFIRM = "ORDER_CONFIRM";
    public static final String OP_ORDER_CANCEL = "ORDER_CANCEL";

    private static final Pattern KEY_FORMAT = Pattern.compile("^[0-9a-fA-F-]{36}$");
    /** 同键本地串行化锁表上限：资金操作键数量有限，超限时整体重置（锁仅用于排队，可安全重建）。 */
    private static final int MAX_KEY_LOCKS = 100_000;

    private final IdempotencyRecordMapper recordMapper;
    /**
     * 同键本地串行化（单实例）：并发 INSERT 相同唯一键会在 MySQL 侧形成
     * 插入意向锁死锁风暴（重试仍互撞）。把同键请求在 JVM 内排队后，
     * 后到者必然走 DuplicateKey → FOR UPDATE 锁读 → 复返 SUCCESS 的文档语义；
     * 多实例部署时退化为数据库行为（死锁重试兜底），正确性不受影响。
     */
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();
    // findAndRegisterModules 注册 classpath 上的 JSR310 等模块：
    // 结果快照（OrderVO 等）携带 LocalDateTime，缺模块会序列化失败
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public <T> T execute(Long userId,
                         String operation,
                         String idempotencyKey,
                         Object requestParams,
                         Class<T> resultType,
                         Supplier<T> businessAction) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ResultCode.IDEMPOTENCY_KEY_INVALID);
        }
        if (keyLocks.size() >= MAX_KEY_LOCKS) {
            keyLocks.clear();
        }
        String lockKey = userId + ":" + operation + ":" + idempotencyKey;
        Object lock = keyLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                return doExecute(userId, operation, idempotencyKey, requestParams, resultType, businessAction);
            }
        } finally {
            keyLocks.remove(lockKey, lock);
        }
    }

    private <T> T doExecute(Long userId,
                            String operation,
                            String idempotencyKey,
                            Object requestParams,
                            Class<T> resultType,
                            Supplier<T> businessAction) {
        String requestHash = sha256Hex(toJson(requestParams));
        String ownerToken = UUID.randomUUID().toString();

        IdempotencyRecord attempt = new IdempotencyRecord();
        attempt.setUserId(userId);
        attempt.setOperation(operation);
        attempt.setIdempotencyKey(idempotencyKey);
        attempt.setRequestHash(requestHash);
        attempt.setOwnerToken(ownerToken);
        attempt.setStatus("PROCESSING");
        attempt.setResultVersion(1);
        try {
            recordMapper.insert(attempt);
        } catch (DuplicateKeyException existingKey) {
            // 旧请求已占有唯一键；所有权由随后的 FOR UPDATE 重读判定，
            // 不依赖本插入的 affected rows 语义。
        }

        IdempotencyRecord current = recordMapper.selectByUniqueForUpdate(userId, operation, idempotencyKey);
        if (current == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "幂等记录读取失败");
        }
        if (!ownerToken.equals(current.getOwnerToken())) {
            if (!requestHash.equals(current.getRequestHash())) {
                log.warn("幂等键参数冲突 userId={} operation={} key={}", userId, operation, idempotencyKey);
                throw new BusinessException(ResultCode.IDEMPOTENCY_CONFLICT);
            }
            if ("SUCCESS".equals(current.getStatus())) {
                log.info("幂等结果复返 userId={} operation={} key={}", userId, operation, idempotencyKey);
                return fromJson(current.getResultSnapshot(), resultType);
            }
            throw new BusinessException(ResultCode.IDEMPOTENCY_PROCESSING);
        }

        T result = businessAction.get();
        int updated = recordMapper.markSuccess(userId, operation, idempotencyKey,
                ownerToken, toJson(result));
        if (updated != 1) {
            log.error("幂等 SUCCESS 条件更新失败 userId={} operation={} key={}", userId, operation, idempotencyKey);
            throw new BusinessException(ResultCode.SERVER_ERROR, "幂等状态提交失败");
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.error("幂等参数/结果序列化失败 type={}", value == null ? "null" : value.getClass().getName(), exception);
            throw new BusinessException(ResultCode.SERVER_ERROR,
                    "请求参数序列化失败：" + exception.getMessage());
        }
    }

    private <T> T fromJson(String snapshot, Class<T> type) {
        try {
            return objectMapper.readValue(snapshot, type);
        } catch (Exception exception) {
            log.error("幂等结果快照反序列化失败 type={}", type.getSimpleName(), exception);
            throw new BusinessException(ResultCode.SERVER_ERROR, "历史结果读取失败");
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
