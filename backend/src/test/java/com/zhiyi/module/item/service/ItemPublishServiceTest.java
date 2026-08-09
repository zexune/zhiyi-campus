package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.trade.mapper.ItemReservationMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemPublishServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.admin.mapper.ViolationReportMapper");
        TableInfoHelper.initTableInfo(assistant, ViolationReport.class);
    }

    @Mock private ItemMapper itemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ViolationReportMapper violationReportMapper;
    @Mock private MarketplaceService marketplaceService;
    @Mock private SysUserMapper userMapper;
    @Mock private ItemReservationMapper reservationMapper;
    @Mock private LocalContentAnalyzer contentAnalyzer;
    @Mock private ItemTagService itemTagService;

    private ItemPublishService service;

    @BeforeEach
    void setUp() {
        service = new ItemPublishService(itemMapper, categoryMapper, violationReportMapper,
                marketplaceService, userMapper, reservationMapper, contentAnalyzer, itemTagService);
    }

    @Test
    void publishesCleanContentWithPassedModerationAndLocalTags() {
        arrangePublisherAndCategory();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of("生活日用", "台灯", "出售")));
        when(itemMapper.insert(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(91L);
            return 1;
        });
        when(marketplaceService.getSnapshot(91L, 7L)).thenReturn(new ItemCardVO());

        service.publish(7L, publishRequest());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).insert(itemCaptor.capture());
        Item inserted = itemCaptor.getValue();
        assertEquals(2L, inserted.getSchoolId());
        assertEquals(ItemStatus.ON_SALE, inserted.getStatus());
        assertEquals(ModerationStatus.PASSED, inserted.getModerationStatus());
        verify(itemTagService).replaceTags(91L, 2L, List.of("生活日用", "台灯", "出售"));
        verify(violationReportMapper, never()).insert(any(ViolationReport.class));
    }

    @Test
    void riskyContentIsPersistedAsReviewingAndCreatesReviewRecord() {
        arrangePublisherAndCategory();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                true, "本地规则命中", List.of("ACADEMIC_MISCONDUCT"), "2026.1", List.of("生活日用")));
        when(itemMapper.insert(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(92L);
            return 1;
        });
        when(violationReportMapper.selectCount(any())).thenReturn(0L);
        when(marketplaceService.getSnapshot(92L, 7L)).thenReturn(new ItemCardVO());

        service.publish(7L, publishRequest());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        ArgumentCaptor<ViolationReport> reportCaptor = ArgumentCaptor.forClass(ViolationReport.class);
        verify(itemMapper).insert(itemCaptor.capture());
        verify(violationReportMapper).insert(reportCaptor.capture());
        assertEquals(ModerationStatus.PENDING, itemCaptor.getValue().getModerationStatus());
        assertEquals(ItemStatus.ON_SALE, itemCaptor.getValue().getStatus());
        assertEquals(ViolationSource.LOCAL_RULE, reportCaptor.getValue().getSource());
        assertEquals(ViolationStatus.PENDING, reportCaptor.getValue().getStatus());
    }

    @Test
    void relistsCorrectedContentBasedOnCurrentModerationState() {
        Item item = new Item();
        item.setId(100L);
        item.setPublisherId(7L);
        item.setSchoolId(2L);
        item.setCategoryId(3L);
        item.setType(ItemType.SELL);
        item.setTitle("Dormitory lamp");
        item.setDescription("Works normally");
        item.setPrice(new BigDecimal("20.00"));
        item.setImages(List.of("/uploads/items/test.jpg"));
        item.setTradeLocation("Canteen entrance");
        item.setStatus(ItemStatus.OFF_SHELF);
        item.setModerationStatus(ModerationStatus.PASSED);
        Category category = new Category();
        category.setId(3L);
        category.setName("Daily goods");
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(categoryMapper.selectById(3L)).thenReturn(category);
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of("Daily goods", "For sale")));
        when(marketplaceService.getSnapshot(100L, 7L)).thenReturn(new ItemCardVO());

        service.relist(7L, 100L);

        assertEquals(ItemStatus.ON_SALE, item.getStatus());
        assertEquals(ModerationStatus.PASSED, item.getModerationStatus());
        verify(itemMapper).updateById(item);
        verify(violationReportMapper, never()).selectCount(any());
    }

    @Test
    void rejectsPublishingWhenUserHasNoSchool() {
        SysUser publisher = new SysUser();
        publisher.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(publisher);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.publish(7L, publishRequest()));

        assertEquals("请先设置所属学校", error.getMessage());
    }

    private void arrangePublisherAndCategory() {
        SysUser publisher = new SysUser();
        publisher.setId(7L);
        publisher.setSchoolId(2L);
        when(userMapper.selectById(7L)).thenReturn(publisher);
        Category category = new Category();
        category.setId(3L);
        category.setName("生活日用");
        when(categoryMapper.selectById(3L)).thenReturn(category);
    }

    private PublishItemDTO publishRequest() {
        PublishItemDTO dto = new PublishItemDTO();
        dto.setType("SELL");
        dto.setTitle("宿舍台灯");
        dto.setDescription("正常使用，功能完好");
        dto.setCategoryId(3L);
        dto.setPrice(new BigDecimal("20.00"));
        dto.setImages(List.of("/uploads/items/test.jpg"));
        dto.setTradeLocation("一食堂门口");
        return dto;
    }
}
