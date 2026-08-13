package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.AdminDashboardVO;
import com.zhiyi.module.admin.vo.TradeHeatmapVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.vo.DailyTradeStatRow;
import com.zhiyi.module.trade.vo.TradeLocationStatRow;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private TradeOrderMapper orderMapper;
    @Mock private ViolationReportMapper violationMapper;
    private AdminDashboardService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize("com.zhiyi.module.user.mapper.SysUserMapper", SysUser.class);
        initialize("com.zhiyi.module.item.mapper.ItemMapper", Item.class);
        initialize("com.zhiyi.module.admin.mapper.ViolationReportMapper", ViolationReport.class);
    }

    private static void initialize(String namespace, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(userMapper, itemMapper, orderMapper, violationMapper);
    }

    @Test
    void dashboardZeroFillsMissingDaysAndFormatsMoney() {
        LocalDate today = LocalDate.now();
        when(userMapper.selectCount(any())).thenReturn(12L);
        when(itemMapper.selectCount(any())).thenReturn(7L);
        when(orderMapper.sumCompletedAmount(any(), any(), any(), isNull())).thenReturn(null);
        when(violationMapper.selectCount(any())).thenReturn(0L);
        when(violationMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectDailyStats(any(), any(), any(), isNull())).thenReturn(List.of(
                new DailyTradeStatRow(today.minusDays(2), 3L, new BigDecimal("27.5"))));

        AdminDashboardVO result = service.getDashboard(null);

        assertEquals(12L, result.getTotalUsers());
        assertEquals(7L, result.getOnSaleItems());
        assertEquals("0.00", result.getTodayTradeAmount());
        assertEquals(7, result.getTrend().size());
        assertEquals(today.minusDays(6).toString(), result.getTrend().getFirst().getDate());
        assertEquals(3L, result.getTrend().get(4).getCount());
        assertEquals("27.50", result.getTrend().get(4).getTotalAmount());
        assertEquals(0L, result.getTrend().getLast().getCount());
    }

    @Test
    void dashboardMapsRecentViolationsAndFallsBackForMissingUsers() {
        ViolationReport known = violation(1L, 10L, "已知发布者");
        ViolationReport missing = violation(2L, 11L, "未知发布者");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setNickname("张同学");
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.selectCount(any())).thenReturn(2L);
        when(orderMapper.sumCompletedAmount(any(), any(), any(), eq(1L)))
                .thenReturn(new BigDecimal("8"));
        when(violationMapper.selectCount(any())).thenReturn(2L);
        when(violationMapper.selectList(any())).thenReturn(List.of(known, missing));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user));
        when(orderMapper.selectDailyStats(any(), any(), any(), eq(1L))).thenReturn(List.of());

        AdminDashboardVO result = service.getDashboard(1L);

        assertEquals("8.00", result.getTodayTradeAmount());
        assertEquals(2L, result.getPendingViolations());
        assertEquals("张同学", result.getRecentViolations().getFirst().getReporterName());
        assertEquals("未知用户", result.getRecentViolations().getLast().getReporterName());
        verify(orderMapper).sumCompletedAmount(any(), any(), any(), eq(1L));
    }

    @Test
    void heatmapPreservesDatabaseOrderingAndSchoolScope() {
        when(orderMapper.selectLocationStats(any(), eq(2L))).thenReturn(List.of(
                new TradeLocationStatRow("图书馆", 8L),
                new TradeLocationStatRow("食堂", 3L)));

        List<TradeHeatmapVO> result = service.getTradeHeatmap(2L);

        assertEquals(List.of("图书馆", "食堂"), result.stream().map(TradeHeatmapVO::getLocation).toList());
        assertEquals(List.of(8L, 3L), result.stream().map(TradeHeatmapVO::getCount).toList());
    }

    private ViolationReport violation(Long id, Long userId, String title) {
        ViolationReport report = new ViolationReport();
        report.setId(id);
        report.setUserId(userId);
        report.setOriginalTitle(title);
        report.setViolationType("KEYWORD_MATCH");
        report.setViolationReason("测试依据");
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }
}
