package com.zhiyi.system;

import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.service.AdminViolationService;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.service.ItemPublishService;
import com.zhiyi.module.item.service.MarketplaceService;
import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.service.TradingEntryService;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.service.BanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §7.1 竞态验收集成测试：双事务 + CyclicBarrier 屏障在真实 MySQL 9.7 上交错执行。
 *
 * 验收性质（见 fix-v3.1.md 第 6/7 节不变量）：
 * - 双下单同商品：恰一成功，I1/I3（uk_order_active_item）与 I10 钱包守恒；
 * - 下单 vs 编辑 / 下单 vs 删除：任何串行顺序下 I1/I2 成立，条件 UPDATE 拒绝输家；
 * - 充值同键并发：owner-token 幂等恰好执行一次（I6/I7）；
 * - 封禁 vs 买家确认收货：恰一方生效，I11/I12/I13 成立；
 * - 违规确认 vs 确认收货：I24（ADMIN_FORCE 恰一条等额 REFUND，REJECTED 无残留挂单）；
 * - 违规确认 vs 编辑：I24（REJECTED 不被 PASSED 写回吞掉，商品绝不回到 ON_SALE）。
 * 交易请求走生产入口 TradingEntryService（事务外准入闸门参与验收）。
 * 仅在 Maven integration profile 中运行（需要 Docker）。
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "zhiyi.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
        "zhiyi.jwt.expiration=1h",
        "zhiyi.upload-path=${java.io.tmpdir}/zhiyi-campus-concurrency-test"
})
class TradingConcurrencyIT {

    private static final String SEED_BCRYPT =
            "$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K";

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // The JUnit Testcontainers extension owns and closes this container.
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:9.7"))
            .withDatabaseName("zhiyi_campus")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/zhiyi_campus_init.sql");

    @Autowired private JdbcTemplate jdbc;
    /** 生产入口：请求先过事务外准入闸门（同商品单飞/全局容量）再进事务，
     *  与 §7.1 "同时进入事务外准入" 的验收语义一致。 */
    @Autowired private TradingEntryService entryService;
    @Autowired private ItemPublishService itemPublishService;
    @Autowired private MarketplaceService marketplaceService;
    @Autowired private BanService banService;
    @Autowired private AdminViolationService adminViolationService;

    // ================================================================
    // 用例 1：双下单同商品 —— 恰一成功（I1/I3/I10）
    // ================================================================

    @Test
    @DisplayName("两个买家同时抢购同一商品：恰一成功，另一明确冲突，无二单无二次扣款")
    void doubleCreateOrderOnSameItemLeavesExactlyOneActiveOrder() throws Exception {
        long seller = insertUser("ccSeller01", "0.00");
        long buyerA = insertUser("ccBuyerA01", "100.00");
        long buyerB = insertUser("ccBuyerB01", "100.00");
        long itemId = insertItem(seller, "并发抢购探针", "30.00");
        try {
            Throwable[] errors = runConcurrently(2, index -> () -> {
                CreateOrderDTO dto = new CreateOrderDTO();
                dto.setItemId(itemId);
                entryService.createOrder(index == 0 ? buyerA : buyerB, dto, UUID.randomUUID().toString());
            });

            long winners = countNulls(errors);
            assertEquals(1L, winners, () -> "恰一买家成功，实际 " + winners + "，错误="
                    + describe(errors));
            Throwable loserThrowable = nonNull(errors);
            assertTrue(loserThrowable instanceof BusinessException,
                    () -> "输家应为业务异常，实际 " + loserThrowable.getClass().getName());
            BusinessException loser = (BusinessException) loserThrowable;
            assertTrue(loser.getCode() == 409 || loser.getCode() == 3004,
                    () -> "输家应得 CONFLICT/TRADE_BUSY，实际 " + loser.getCode());

            // I1/I3：恰一笔进行中订单，商品 RESERVED；I10：恰一次扣款
            assertEquals(1L, count("SELECT COUNT(*) FROM trade_order WHERE item_id = ? AND status = 'WAITING_MEET'", itemId));
            assertEquals("RESERVED", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
            assertEquals(1L, count("SELECT COUNT(*) FROM wallet_log WHERE type = 'PAYMENT' AND order_id IN "
                    + "(SELECT id FROM trade_order WHERE item_id = ?)", itemId));
        } finally {
            cleanup(itemId, seller, buyerA, buyerB);
        }
    }

    // ================================================================
    // 用例 2：下单 vs 编辑 —— 任何交错下 I1/I2 成立（B4/B5 验收）
    // ================================================================

    @Test
    @DisplayName("下单与商品编辑并发：条件 UPDATE 拒绝输家，订单与商品状态始终一致")
    void createOrderVsEditKeepsOrderItemInvariant() throws Exception {
        long seller = insertUser("ccSeller02", "0.00");
        long buyer = insertUser("ccBuyer02", "100.00");
        long itemId = insertItem(seller, "下单与编辑竞态探针", "20.00");
        try {
            Throwable[] errors = runConcurrently(2, index -> () -> {
                if (index == 0) {
                    CreateOrderDTO dto = new CreateOrderDTO();
                    dto.setItemId(itemId);
                    entryService.createOrder(buyer, dto, UUID.randomUUID().toString());
                } else {
                    itemPublishService.update(seller, itemId, editRequest(itemId));
                }
            });

            long activeOrders = count(
                    "SELECT COUNT(*) FROM trade_order WHERE item_id = ? AND status = 'WAITING_MEET'", itemId);
            String itemStatus = scalar("SELECT status FROM item WHERE id = ?", String.class, itemId);

            // I1/I2：订单存在当且仅当商品 RESERVED —— 编辑绝不把 RESERVED 写回 ON_SALE
            if (activeOrders == 1L) {
                assertEquals("RESERVED", itemStatus,
                        () -> "存在进行中订单但商品非 RESERVED（编辑写回违例），交错=" + describe(errors));
            } else {
                assertEquals(0L, activeOrders);
                // 订单不存在：编辑可能成功（商品资料已更新且仍可售）——不允许出现 RESERVED 幽灵
                assertTrue(!"RESERVED".equals(itemStatus),
                        () -> "无订单但商品 RESERVED（下单半提交违例），交错=" + describe(errors));
            }
        } finally {
            cleanup(itemId, seller, buyer);
        }
    }

    // ================================================================
    // 用例 3：下单 vs 删除 —— 条件软删拒绝输家
    // ================================================================

    @Test
    @DisplayName("下单与删除商品并发：有订单则商品绝不会被软删")
    void createOrderVsDeleteNeverDeletesItemWithActiveOrder() throws Exception {
        long seller = insertUser("ccSeller03", "0.00");
        long buyer = insertUser("ccBuyer03", "100.00");
        long itemId = insertItem(seller, "下单与删除竞态探针", "20.00");
        try {
            Throwable[] errors = runConcurrently(2, index -> () -> {
                if (index == 0) {
                    CreateOrderDTO dto = new CreateOrderDTO();
                    dto.setItemId(itemId);
                    entryService.createOrder(buyer, dto, UUID.randomUUID().toString());
                } else {
                    marketplaceService.deleteOwnItem(seller, itemId);
                }
            });

            long activeOrders = count(
                    "SELECT COUNT(*) FROM trade_order WHERE item_id = ? AND status = 'WAITING_MEET'", itemId);
            long deleted = count(
                    "SELECT COUNT(*) FROM item WHERE id = ? AND is_deleted = 1", itemId);

            // 有订单 ⇒ 商品未删除且 RESERVED；删除成功 ⇒ 不可能出现订单
            if (activeOrders == 1L) {
                assertEquals(0L, deleted, () -> "存在进行中订单但商品被删除（B5 违例），交错=" + describe(errors));
                assertEquals("RESERVED", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
            } else {
                assertEquals(0L, activeOrders);
                // 删除赢家或双输（下单 NOT_FOUND）皆合法；商品不得处于 RESERVED
            }
        } finally {
            cleanup(itemId, seller, buyer);
        }
    }

    // ================================================================
    // 用例 4：充值同键并发 ×10 —— owner-token 幂等恰好执行一次（I6/I7）
    // ================================================================

    @Test
    @DisplayName("同幂等键并发充值十次：余额只增加一次，恰好一条流水与一条 SUCCESS 记录")
    void rechargeWithSameKeyExecutesExactlyOnce() throws Exception {
        long user = insertUser("ccRecharge01", "50.00");
        String key = UUID.randomUUID().toString();
        try {
            Throwable[] errors = runConcurrently(10, index -> ()
                    -> entryService.recharge(user, new BigDecimal("10.00"), key));

            assertEquals(10L, countNulls(errors), () -> "全部调用都应成功复返，错误=" + describe(errors));
            // I10：余额恰好 +10；I7：恰一条 RECHARGE 流水；I6：恰一条 SUCCESS 幂等记录
            assertEquals(new BigDecimal("60.00"), scalar(
                    "SELECT wallet_balance FROM sys_user WHERE id = ?", BigDecimal.class, user));
            assertEquals(1L, count("SELECT COUNT(*) FROM wallet_log WHERE user_id = ? AND type = 'RECHARGE'", user));
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM idempotency_record WHERE user_id = ? AND idempotency_key = ? AND status = 'SUCCESS'",
                    user, key));
        } finally {
            cleanup(null, user);
        }
    }

    // ================================================================
    // 用例 5：封禁 vs 买家确认收货 —— 恰一方生效（I11/I12/I13）
    // ================================================================

    @Test
    @DisplayName("封禁买家与其确认收货并发：确认胜则 COMPLETED 不追溯，封禁胜则 AUTO_CANCEL 等额退款")
    void banVsBuyerConfirmExactlyOneSideTakesEffect() throws Exception {
        long seller = insertUser("ccSeller04", "0.00");
        long buyer = insertUser("ccBuyer04", "100.00");
        long itemId = insertItem(seller, "封禁与确认竞态探针", "40.00");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setItemId(itemId);
        entryService.createOrder(buyer, dto, UUID.randomUUID().toString());
        long orderId = scalar("SELECT id FROM trade_order WHERE item_id = ?", Long.class, itemId);
        try {
            Throwable[] errors = runConcurrently(2, index -> () -> {
                if (index == 0) {
                    BanUserDTO ban = new BanUserDTO();
                    ban.setUserId(buyer);
                    ban.setType("BAN_TEMP");
                    ban.setBanDays(3);
                    ban.setReason("竞态验收封禁");
                    banService.punish(ban, seedAdminId());
                } else {
                    entryService.confirmReceipt(orderId, buyer, UUID.randomUUID().toString());
                }
            });

            String orderStatus = scalar("SELECT status FROM trade_order WHERE id = ?", String.class, orderId);
            String cancelReason = scalar("SELECT cancel_reason FROM trade_order WHERE id = ?", String.class, orderId);
            if ("COMPLETED".equals(orderStatus)) {
                // 确认胜：不追溯退款（已知接受边界），封禁事务无单可撤
                assertNull(cancelReason);
                assertEquals(0L, count("SELECT COUNT(*) FROM wallet_log WHERE order_id = ? AND type = 'REFUND'", orderId));
                assertEquals("SOLD", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
            } else {
                // 封禁胜：AUTO_CANCEL + 等额退款 + 商品 OFF_SHELF（I11/I12/I13）
                assertEquals("CANCELLED", orderStatus, () -> "交错=" + describe(errors));
                assertEquals("AUTO_CANCEL", cancelReason);
                assertEquals(1L, count(
                        "SELECT COUNT(*) FROM wallet_log WHERE order_id = ? AND type = 'REFUND' AND amount = 40.00",
                        orderId));
                assertEquals("OFF_SHELF", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
                assertEquals(new BigDecimal("100.00"), scalar(
                        "SELECT wallet_balance FROM sys_user WHERE id = ?", BigDecimal.class, buyer));
            }
            // I11：封禁提交后买家无进行中订单；买家已封禁（两路径封禁均成功）
            assertEquals(0L, count(
                    "SELECT COUNT(*) FROM trade_order WHERE buyer_id = ? AND status = 'WAITING_MEET'", buyer));
            assertEquals("BANNED_TEMP", scalar("SELECT status FROM sys_user WHERE id = ?", String.class, buyer));
        } finally {
            cleanup(itemId, seller, buyer);
        }
    }

    // ================================================================
    // 用例 6：违规确认 vs 确认收货 —— I24
    // ================================================================

    @Test
    @DisplayName("违规强制撤单与买家确认收货并发：ADMIN_FORCE 恰一条等额退款，REJECTED 无残留挂单")
    void violationConfirmVsBuyerConfirmKeepsI24() throws Exception {
        long seller = insertUser("ccSeller05", "0.00");
        long buyer = insertUser("ccBuyer05", "100.00");
        long itemId = insertItem(seller, "违规与确认竞态探针", "25.00");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setItemId(itemId);
        entryService.createOrder(buyer, dto, UUID.randomUUID().toString());
        long orderId = scalar("SELECT id FROM trade_order WHERE item_id = ?", Long.class, itemId);
        long reportId = insertPendingReport(itemId, seller);
        try {
            Throwable[] errors = runConcurrently(2, index -> () -> {
                if (index == 0) {
                    ConfirmViolationDTO confirm = new ConfirmViolationDTO();
                    confirm.setReason("竞态验收违规确认");
                    confirm.setHandleNote("确认违规");
                    adminViolationService.confirmViolation(reportId, confirm, seedAdminId());
                } else {
                    entryService.confirmReceipt(orderId, buyer, UUID.randomUUID().toString());
                }
            });

            String orderStatus = scalar("SELECT status FROM trade_order WHERE id = ?", String.class, orderId);
            String cancelReason = scalar("SELECT cancel_reason FROM trade_order WHERE id = ?", String.class, orderId);
            String moderation = scalar(
                    "SELECT moderation_status FROM item WHERE id = ?", String.class, itemId);
            if ("CANCELLED".equals(orderStatus)) {
                // 违规胜：ADMIN_FORCE + 等额退款 + REJECTED + OFF_SHELF（I24）
                assertEquals("ADMIN_FORCE", cancelReason);
                assertEquals(1L, count(
                        "SELECT COUNT(*) FROM wallet_log WHERE order_id = ? AND type = 'REFUND' AND amount = 25.00",
                        orderId));
                assertEquals("OFF_SHELF", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
                assertEquals("REJECTED", moderation);
            } else {
                // 确认胜：无单可撤、SOLD 保持、不追溯退款；违规确认仍完成投影
                assertEquals("COMPLETED", orderStatus, () -> "交错=" + describe(errors));
                assertEquals(0L, count("SELECT COUNT(*) FROM wallet_log WHERE order_id = ? AND type = 'REFUND'", orderId));
                assertEquals("SOLD", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
            }
            // I24：REJECTED（或已投影）商品不存在 WAITING_MEET 订单
            assertEquals(0L, count(
                    "SELECT COUNT(*) FROM trade_order WHERE item_id = ? AND status = 'WAITING_MEET'", itemId));
            assertEquals("CONFIRMED", scalar(
                    "SELECT status FROM violation_report WHERE id = ?", String.class, reportId));
        } finally {
            cleanup(itemId, seller, buyer);
        }
    }

    // ================================================================
    // 用例 7：违规确认 vs 编辑 —— I24（REJECTED 不被编辑写回吞掉）
    // ================================================================

    @Test
    @DisplayName("违规确认与商品编辑并发：REJECTED 绝不被写回吞掉，商品绝不回到 ON_SALE")
    void violationConfirmVsEditNeverLosesRejection() throws Exception {
        // 多轮新夹具交错，覆盖"编辑者读到旧 PASSED、违规确认先提交、编辑 UPDATE 后到"
        // 的危险串行化；confirmViolation 持商品行锁 FOR UPDATE，编辑的条件 UPDATE
        // 只会在确认提交后重评估 WHERE，任何串行顺序都不允许 moderation 回到 PASSED。
        for (int round = 1; round <= 5; round++) {
            final int roundNo = round;
            long seller = insertUser("ccSeller06r" + round, "0.00");
            long itemId = insertItem(seller, "违规确认与编辑竞态探针" + round, "20.00");
            long reportId = insertPendingReport(itemId, seller);
            try {
                Throwable[] errors = runConcurrently(2, index -> () -> {
                    if (index == 0) {
                        ConfirmViolationDTO confirm = new ConfirmViolationDTO();
                        confirm.setReason("竞态验收违规确认");
                        confirm.setHandleNote("确认违规");
                        adminViolationService.confirmViolation(reportId, confirm, seedAdminId());
                    } else {
                        itemPublishService.update(seller, itemId, editRequest(itemId));
                    }
                });

                String moderation = scalar(
                        "SELECT moderation_status FROM item WHERE id = ?", String.class, itemId);
                String itemStatus = scalar("SELECT status FROM item WHERE id = ?", String.class, itemId);
                // 审核决定本身必须落地
                assertEquals("CONFIRMED", scalar(
                        "SELECT status FROM violation_report WHERE id = ?", String.class, reportId),
                        () -> "round=" + roundNo + " 交错=" + describe(errors));
                // I24：REJECTED（编辑被拒/编辑先提交后被投影覆盖）或 PENDING（编辑者
                // 读到 REJECTED 走整改分支）均合法；PASSED 意味着 REJECTED 被写回吞掉
                assertTrue("REJECTED".equals(moderation) || "PENDING".equals(moderation),
                        () -> "违规确认被编辑的 PASSED 写回吞掉（I24 违例），round=" + roundNo
                                + " moderation=" + moderation + " 交错=" + describe(errors));
                assertEquals("OFF_SHELF", itemStatus,
                        () -> "违规商品必须下架，round=" + roundNo + " 交错=" + describe(errors));
            } finally {
                cleanup(itemId, seller);
            }
        }
    }

    // ================================================================
    // 并发执行与数据准备辅助
    // ================================================================

    /** 屏障并发执行：所有线程 await 同一 CyclicBarrier 后同时进入（带 0-25ms 到达抖动，
     *  模拟真实请求差并降低同步重试互撞），收集各自异常。 */
    private Throwable[] runConcurrently(int parties, IntFunction<Runnable> tasks) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(parties);
        Throwable[] errors = new Throwable[parties];
        ExecutorService pool = Executors.newFixedThreadPool(parties);
        try {
            List<Future<Void>> futures = new ArrayList<>(parties);
            for (int i = 0; i < parties; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 25));
                    try {
                        tasks.apply(index).run();
                    } catch (Throwable failure) {
                        errors[index] = failure;
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return errors;
    }

    private long countNulls(Throwable[] errors) {
        long count = 0;
        for (Throwable error : errors) {
            if (error == null) count++;
        }
        return count;
    }

    private Throwable nonNull(Throwable[] errors) {
        for (Throwable error : errors) {
            if (error != null) return error;
        }
        return null;
    }

    private String describe(Throwable[] errors) {
        StringBuilder sb = new StringBuilder("[");
        for (Throwable error : errors) {
            sb.append(error == null ? "OK" : error.getClass().getSimpleName()
                    + "(" + (error instanceof BusinessException be ? be.getCode() : error.getMessage()) + ")");
            sb.append(", ");
        }
        return sb.append("]").toString();
    }

    private long seedAdminId() {
        return scalar("SELECT id FROM sys_user WHERE role = 'ADMIN' AND is_system = 0", Long.class);
    }

    private long insertUser(String studentId, String balance) {
        jdbc.update("""
                INSERT INTO sys_user (student_id, password, nickname, school_id, role, status,
                                      level, exp, wallet_balance, profile_version, security_question, security_answer)
                VALUES (?, ?, ?, 1, 'USER', 'ACTIVE', 1, 0, ?, 0, '系统预设问题', ?)
                """, studentId, SEED_BCRYPT, studentId, balance, SEED_BCRYPT);
        return scalar("SELECT id FROM sys_user WHERE student_id = ?", Long.class, studentId);
    }

    private long insertItem(long publisherId, String title, String price) {
        jdbc.update("""
                INSERT INTO item (publisher_id, school_id, type, title, description, category_id, price,
                                  images, moderation_status, status, feed_key, listing_revision, is_deleted)
                VALUES (?, 1, 'SELL', ?, '并发验收夹具', 1, ?,
                        JSON_ARRAY('/uploads/items/concurrency.png'), 'PASSED', 'ON_SALE',
                        FLOOR(RAND() * 9000000000000000000), 1, 0)
                """, publisherId, title, price);
        return scalar("SELECT id FROM item WHERE title = ?", Long.class, title);
    }

    private long insertPendingReport(long itemId, long sellerId) {
        jdbc.update("""
                INSERT INTO violation_report (user_id, original_title, original_description, source,
                                              violation_type, violation_reason, status, item_id)
                VALUES (?, '竞态验收商品', '夹具描述', 'LOCAL_RULE', 'KEYWORD_MATCH', '竞态验收', 'PENDING', ?)
                """, sellerId, itemId);
        return scalar("SELECT id FROM violation_report WHERE item_id = ? AND status = 'PENDING'", Long.class, itemId);
    }

    private PublishItemDTO editRequest(long itemId) {
        String title = scalar("SELECT title FROM item WHERE id = ?", String.class, itemId);
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType("SELL");
        dto.setTitle(title + "（已编辑）");
        dto.setDescription("并发验收编辑后的描述");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("20.00"));
        dto.setImages(List.of("/uploads/items/concurrency.png"));
        dto.setTradeLocation("图书馆");
        return dto;
    }

    /** 用例末尾尽力清理夹具（按外键逆序）。 */
    private void cleanup(Long itemId, long... userIds) {
        if (itemId != null) {
            jdbc.update("DELETE FROM wallet_log WHERE order_id IN (SELECT id FROM trade_order WHERE item_id = ?)", itemId);
            jdbc.update("DELETE FROM idempotency_record WHERE operation LIKE 'ORDER%' AND user_id IN "
                    + "(SELECT buyer_id FROM trade_order WHERE item_id = ?)", itemId);
            jdbc.update("DELETE FROM trade_order WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM reputation_penalty WHERE report_id IN "
                    + "(SELECT id FROM violation_report WHERE item_id = ?)", itemId);
            jdbc.update("DELETE FROM violation_report WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM item_favorite WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM item_tag WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM item WHERE id = ?", itemId);
        }
        for (long userId : userIds) {
            jdbc.update("DELETE FROM idempotency_record WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM wallet_log WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM exp_log WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM violation_log WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM chat_message WHERE sender_id = ? OR receiver_id = ?", userId, userId);
            jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
        // outbox_event / view_flush 无外键约束，直接清空夹具期间的事件
        jdbc.update("DELETE FROM outbox_event");
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        assertNotNull(value);
        return value;
    }

    private <T> T scalar(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbc.queryForList(sql, type, args);
        assertEquals(1L, rows.size(), () -> "期望恰好一行: " + sql);
        return rows.getFirst();
    }
}
