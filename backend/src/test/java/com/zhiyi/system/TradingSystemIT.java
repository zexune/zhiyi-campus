package com.zhiyi.system;

import com.zhiyi.module.trade.dto.CreateOrderDTO;
import com.zhiyi.module.trade.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实系统边界测试：Spring 上下文、JWT/角色拦截器、MVC、事务、MyBatis 自定义 SQL
 * 与 MySQL 9.7 LTS 均参与执行。仅在 Maven integration profile 中运行。
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "zhiyi.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
        "zhiyi.jwt.expiration=1h",
        "zhiyi.upload-path=${java.io.tmpdir}/zhiyi-campus-system-test"
})
@AutoConfigureMockMvc
class TradingSystemIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] TEST_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // The JUnit Testcontainers extension owns and closes this container.
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:9.7"))
            .withDatabaseName("zhiyi_campus")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/zhiyi_campus_init.sql");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderService orderService;

    @Test
    @DisplayName("集成环境使用 MySQL 9.7 LTS 数据库基线")
    void runsAgainstMySql97Lts() {
        String version = scalar("SELECT VERSION()", String.class);
        assertTrue(version.startsWith("9.7."), () -> "Expected MySQL 9.7.x but got " + version);
    }

    @Test
    @DisplayName("OpenAPI 文档发布真实 HTTP 契约，并区分公开与 Bearer 受保护接口")
    void openApiDocumentExposesSecurityContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.info.title").value("智易校园 API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/order/create'].post").exists());
    }

    @Test
    @Transactional
    @DisplayName("用户从注册、发布、充值、下单、确认到评价可完整跑通，且角色边界生效")
    void completeTradingJourneyPersistsConsistentState() throws Exception {
        long initialUserCount = count("SELECT COUNT(*) FROM sys_user WHERE role = 'USER'");
        Session seller = register("itSeller01", "系统卖家");
        Session buyer = register("itBuyer01", "系统买家");

        MockMultipartFile image = new MockMultipartFile(
                "file", "system-test.png", MediaType.IMAGE_PNG_VALUE, TEST_PNG);
        JsonNode uploaded = body(mockMvc.perform(multipart("/api/item/upload-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/uploads/items/")))
                .andReturn());
        String imageUrl = uploaded.required("data").required("url").stringValue();

        JsonNode published = body(mockMvc.perform(post("/api/item/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"SELL",
                                  "title":"系统测试教材",
                                  "description":"九成新教材，校内当面交易",
                                  "categoryId":2,
                                  "price":19.90,
                                  "images":["%s"],
                                  "tradeLocation":"图书馆南门"
                                }
                                """.formatted(imageUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.moderationStatus").value("PASSED"))
                .andReturn());
        long itemId = published.get("data").get("id").asLong();

        mockMvc.perform(post("/api/wallet/recharge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(100.00));

        JsonNode created = body(mockMvc.perform(post("/api/order/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("WAITING_MEET"))
                .andReturn());
        long orderId = created.get("data").get("id").asLong();

        mockMvc.perform(put("/api/order/{id}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(post("/api/order/{id}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"accurate\":true,\"comment\":\"交易顺利\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(new BigDecimal("80.10"), balanceOf(buyer.id()));
        assertEquals(new BigDecimal("19.90"), balanceOf(seller.id()));
        assertEquals("COMPLETED", scalar("SELECT status FROM trade_order WHERE id = ?", String.class, orderId));
        assertEquals("SOLD", scalar("SELECT status FROM item WHERE id = ?", String.class, itemId));
        assertEquals(0L, count("SELECT COUNT(*) FROM item_reservation WHERE item_id = ?", itemId));
        assertEquals(1L, count("SELECT COUNT(*) FROM trade_review WHERE order_id = ?", orderId));
        assertEquals(2L, count("SELECT COUNT(*) FROM wallet_log WHERE user_id = ?", buyer.id()));
        assertEquals(1L, count("SELECT COUNT(*) FROM wallet_log WHERE user_id = ?", seller.id()));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        JsonNode adminLogin = body(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andReturn());
        String adminToken = adminLogin.required("data").required("token").stringValue();
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(initialUserCount + 2));
    }

    @Test
    @Transactional
    @DisplayName("管理端用户列表支持学校精确与学号/昵称/邮箱/手机号模糊筛选，且不返回管理员账号")
    void adminUserListFiltersBySchoolAndFuzzyKeywordFields() throws Exception {
        Session probeA = register("22224101", "筛选探针甲");
        register("22224102", "筛选探针乙");
        jdbc.update("""
                INSERT INTO sys_user
                    (student_id, password, nickname, school_id, role, status, level, exp,
                     wallet_balance, security_question, security_answer)
                VALUES ('22224103', 'test-hash', '筛选探针丙', 2, 'USER', 'ACTIVE', 1, 0, 0, '测试问题', 'test-hash')
                """);
        jdbc.update("UPDATE sys_user SET school_email = 'probe@shu.edu.cn', phone = '13800001111' WHERE id = ?", probeA.id());

        JsonNode adminLogin = body(mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        String adminToken = adminLogin.required("data").required("token").stringValue();

        // 学号模糊命中单条，并批量带出学校名称与联系方式
        JsonNode byStudentId = adminSearchUsers(adminToken, Map.of("studentId", "22224101"));
        assertEquals(1, byStudentId.required("data").required("total").asLong());
        JsonNode records = byStudentId.required("data").required("records");
        assertEquals(1, records.size());
        JsonNode row = records.get(0);
        assertEquals("筛选探针甲", row.required("nickname").stringValue());
        assertEquals("上海大学", row.required("schoolName").stringValue());
        assertEquals("probe@shu.edu.cn", row.required("schoolEmail").stringValue());

        // 昵称模糊一次命中三条；邮箱/手机号模糊只命中补全过资料的探针甲
        assertEquals(3, adminSearchUsers(adminToken, Map.of("nickname", "筛选探针")).required("data").required("total").asLong());
        assertEquals(1, adminSearchUsers(adminToken, Map.of("email", "probe@")).required("data").required("total").asLong());
        assertEquals(1, adminSearchUsers(adminToken, Map.of("phone", "1380000111")).required("data").required("total").asLong());
        // 学校为精确筛选：schoolId=2 只剩探针丙，再叠加学号条件后为空交集
        assertEquals(1, adminSearchUsers(adminToken, Map.of("schoolId", "2")).required("data").required("total").asLong());
        assertEquals(0, adminSearchUsers(adminToken, Map.of("schoolId", "2", "studentId", "22224101")).required("data").required("total").asLong());
        // 种子管理员（学号 admin）固定 role=ADMIN，不进入普通用户列表
        assertEquals(0, adminSearchUsers(adminToken, Map.of("studentId", "admin")).required("data").required("total").asLong());
    }

    private JsonNode adminSearchUsers(String adminToken, Map<String, String> params) throws Exception {
        var request = get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(adminToken));
        params.forEach(request::param);
        return body(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
    }

    @Test
    @DisplayName("事务末端写流水失败时，余额、预留与订单必须整体回滚")
    void orderTransactionRollsBackWhenFinalPersistenceStepFails() {
        long sellerId = insertUser("rollbackSeller", "回滚卖家", new BigDecimal("0.00"));
        long buyerId = insertUser("rollbackBuyer", "回滚买家", new BigDecimal("50.00"));
        jdbc.update("""
                INSERT INTO item
                    (publisher_id, school_id, type, title, description, category_id, price, images,
                     moderation_status, trade_location, status, feed_key, is_deleted)
                VALUES (?, 1, 'SELL', '事务回滚探针', '验证订单事务原子性', 1, 12.50,
                        JSON_ARRAY('/uploads/rollback.png'), 'PASSED', '测试地点', 'ON_SALE', 90001, 0)
                """, sellerId);
        long itemId = scalar("SELECT id FROM item WHERE title = '事务回滚探针'", Long.class);

        jdbc.execute("ALTER TABLE wallet_log ADD CONSTRAINT system_test_reject_payment CHECK (type <> 'PAYMENT')");
        try {
            CreateOrderDTO request = new CreateOrderDTO();
            request.setItemId(itemId);

            assertThrows(RuntimeException.class, () -> orderService.createOrder(buyerId, request));

            assertEquals(new BigDecimal("50.00"), balanceOf(buyerId));
            assertEquals(0L, count("SELECT COUNT(*) FROM trade_order WHERE item_id = ?", itemId));
            assertEquals(0L, count("SELECT COUNT(*) FROM item_reservation WHERE item_id = ?", itemId));
        } finally {
            jdbc.execute("ALTER TABLE wallet_log DROP CHECK system_test_reject_payment");
            jdbc.update("DELETE FROM wallet_log WHERE user_id IN (?, ?)", buyerId, sellerId);
            jdbc.update("DELETE FROM item_reservation WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM trade_order WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM item WHERE id = ?", itemId);
            jdbc.update("DELETE FROM sys_user WHERE id IN (?, ?)", buyerId, sellerId);
        }
    }

    private Session register(String studentId, String nickname) throws Exception {
        JsonNode response = body(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":1,
                                  "studentId":"%s",
                                  "password":"123456",
                                  "confirmPassword":"123456",
                                  "nickname":"%s",
                                  "securityQuestion":"测试问题？",
                                  "securityAnswer":"测试答案"
                                }
                                """.formatted(studentId, nickname)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        JsonNode data = response.required("data");
        return new Session(
                data.required("user").required("id").asLong(),
                data.required("token").stringValue());
    }

    private long insertUser(String studentId, String nickname, BigDecimal balance) {
        jdbc.update("""
                INSERT INTO sys_user
                    (student_id, password, nickname, school_id, role, status, level, exp,
                     wallet_balance, security_question, security_answer)
                VALUES (?, 'test-hash', ?, 1, 'USER', 'ACTIVE', 1, 0, ?, '测试问题', 'test-hash')
                """, studentId, nickname, balance);
        return scalar("SELECT id FROM sys_user WHERE school_id = 1 AND student_id = ?", Long.class, studentId);
    }

    private BigDecimal balanceOf(long userId) {
        return scalar("SELECT wallet_balance FROM sys_user WHERE id = ?", BigDecimal.class, userId);
    }

    private long count(String sql, Object... args) {
        return scalar(sql, Long.class, args);
    }

    private <T> T scalar(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, args);
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(long id, String token) {
    }
}
