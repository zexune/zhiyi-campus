package com.zhiyi.module.item.service;

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
import com.zhiyi.module.item.mapper.ItemViewStatMapper;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.UploadImageVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：ItemReservationMapper 依赖已移除；发布会初始化独立浏览统计行
 * （ItemViewStatMapper）并分配 listing_revision（feed_sequence 序列）。
 */
@ExtendWith(MockitoExtension.class)
class ItemPublishServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ViolationReport.class, ViolationReportMapper.class);
    }

    @Mock private ItemMapper itemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ViolationReportMapper violationReportMapper;
    @Mock private MarketplaceService marketplaceService;
    @Mock private SysUserMapper userMapper;
    @Mock private LocalContentAnalyzer contentAnalyzer;
    @Mock private ItemTagService itemTagService;
    @Mock private ItemViewStatMapper viewStatMapper;

    @TempDir Path uploadDirectory;

    private ItemPublishService service;

    @BeforeEach
    void setUp() {
        service = new ItemPublishService(itemMapper, categoryMapper, violationReportMapper,
                marketplaceService, userMapper, contentAnalyzer, itemTagService, viewStatMapper);
        ReflectionTestUtils.setField(service, "uploadPath", uploadDirectory.toString());
    }

    @Test
    void acceptsImagesWhenMagicNumbersMatchDeclaredFormats() throws IOException {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d
        };
        byte[] jpeg = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0,
                0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        byte[] webp = {
                0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50
        };

        UploadImageVO uploadedPng = service.uploadImage(
                new MockMultipartFile("file", "campus.png", "image/png", png));
        UploadImageVO uploadedJpeg = service.uploadImage(
                new MockMultipartFile("file", "campus.jpeg", "image/jpeg", jpeg));
        UploadImageVO uploadedWebp = service.uploadImage(
                new MockMultipartFile("file", "campus.webp", "image/webp", webp));

        assertTrue(uploadedPng.getUrl().endsWith(".png"));
        assertTrue(uploadedJpeg.getUrl().endsWith(".jpg"));
        assertTrue(uploadedWebp.getUrl().endsWith(".webp"));
        try (var files = Files.walk(uploadDirectory)) {
            assertEquals(3L, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void rejectsExecutableContentDisguisedAsJpegBeforeWritingIt() throws IOException {
        byte[] executable = {0x4d, 0x5a, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", executable);

        assertThrows(BusinessException.class, () -> service.uploadImage(file));

        try (var files = Files.walk(uploadDirectory)) {
            assertEquals(0L, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void rejectsWhenMagicNumberConflictsWithFilenameAndContentType() {
        byte[] jpeg = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0,
                0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", jpeg);

        assertThrows(BusinessException.class, () -> service.uploadImage(file));
    }

    @Test
    void rejectsWhenFilenameExtensionConflictsWithContentType() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/png", png);

        assertThrows(BusinessException.class, () -> service.uploadImage(file));
    }

    @Test
    void publishesCleanContentWithPassedModerationAndLocalTags() {
        arrangePublisherAndCategory();
        arrangeListingRevision();
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
        // 发布分配 listing_revision 并初始化独立浏览统计行
        assertEquals(41L, inserted.getListingRevision());
        verify(viewStatMapper).insert(any(com.zhiyi.module.item.entity.ItemViewStat.class));
        verify(itemTagService).replaceTags(91L, 2L, List.of("生活日用", "台灯", "出售"));
        verify(violationReportMapper, never()).insert(any(ViolationReport.class));
    }

    @Test
    void riskyContentIsPersistedAsReviewingAndCreatesReviewRecord() {
        arrangePublisherAndCategory();
        arrangeListingRevision();
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
        when(userMapper.selectById(7L)).thenReturn(publisher());
        when(categoryMapper.selectById(3L)).thenReturn(category);
        arrangeListingRevision();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of("Daily goods", "For sale")));
        // toReviewDTO 会带上商品现有标签（视为用户提供的 tags），需为清洗调用打桩
        when(itemTagService.tagsByItemIds(any())).thenReturn(Map.of());
        when(contentAnalyzer.sanitizeUserTags(any())).thenReturn(new LocalContentAnalyzer.TagCheck(
                List.of("Daily goods", "For sale"), false, "", List.of()));
        when(itemMapper.update(any(Item.class), any())).thenReturn(1);
        when(marketplaceService.getSnapshot(100L, 7L)).thenReturn(new ItemCardVO());

        service.relist(7L, 100L);

        // 重上架走 patch 实体 + 条件 UPDATE（WHERE status='OFF_SHELF'），不做整实体写回
        ArgumentCaptor<Item> patchCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).update(patchCaptor.capture(), any());
        Item patch = patchCaptor.getValue();
        assertEquals(ItemStatus.ON_SALE, patch.getStatus());
        assertEquals(ModerationStatus.PASSED, patch.getModerationStatus());
        // 重新上架分配新 listing_revision + 刷新发布者层级键，使商品退出旧游标快照
        assertEquals(41L, patch.getListingRevision());
        assertEquals("宝山校区", patch.getPublisherCampusKey());
        verify(itemMapper, never()).updateById(any(Item.class));
        verify(violationReportMapper, never()).selectCount(any());
    }

    @Test
    void relistFailsWhenStatusLeftOffShelfConcurrently() {
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
        item.setStatus(ItemStatus.OFF_SHELF);
        item.setModerationStatus(ModerationStatus.PASSED);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(userMapper.selectById(7L)).thenReturn(publisher());
        when(categoryMapper.selectById(3L)).thenReturn(new Category());
        arrangeListingRevision();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of()));
        when(itemTagService.tagsByItemIds(any())).thenReturn(Map.of());
        when(contentAnalyzer.sanitizeUserTags(any())).thenReturn(new LocalContentAnalyzer.TagCheck(
                List.of(), false, "", List.of()));
        // 竞态窗口：无锁读为 OFF_SHELF 后，并发事务（下单/审核迁移）使状态离开 OFF_SHELF
        when(itemMapper.update(any(Item.class), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.relist(7L, 100L));

        assertEquals(409, error.getCode());
        verify(itemTagService, never()).replaceTags(any(), any(), any());
    }

    @Test
    void updateWritesOnlyEditedFieldsViaConditionalUpdate() {
        Item item = new Item();
        item.setId(100L);
        item.setPublisherId(7L);
        item.setSchoolId(2L);
        item.setCategoryId(3L);
        item.setType(ItemType.SELL);
        item.setTitle("旧标题");
        item.setDescription("旧描述");
        item.setPrice(new BigDecimal("20.00"));
        item.setImages(List.of("/uploads/items/test.jpg"));
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(ModerationStatus.PASSED);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(categoryMapper.selectById(3L)).thenReturn(new Category());
        arrangeListingRevision();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of()));
        when(itemMapper.update(any(Item.class), any())).thenReturn(1);
        when(marketplaceService.getSnapshot(100L, 7L)).thenReturn(new ItemCardVO());

        service.update(7L, 100L, publishRequest());

        ArgumentCaptor<Item> patchCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).update(patchCaptor.capture(), any());
        Item patch = patchCaptor.getValue();
        // B4 根因验证：patch 只携带编辑字段；正常分支不写回 status（null 字段不进 SET）
        assertEquals("宿舍台灯", patch.getTitle());
        assertEquals(new BigDecimal("20.00"), patch.getPrice());
        assertEquals(41L, patch.getListingRevision());
        assertEquals(ModerationStatus.PASSED, patch.getModerationStatus());
        org.junit.jupiter.api.Assertions.assertNull(patch.getStatus());
        // 整实体写回路径必须绝迹
        verify(itemMapper, never()).updateById(any(Item.class));
    }

    @Test
    void updateFailsWhenItemMigratedConcurrently() {
        // §7.1 "下单 vs 编辑" 交错的单元级决胜点：
        // 编辑者无锁读到 ON_SALE/PASSED → 并发下单把商品置 RESERVED →
        // 条件 UPDATE（WHERE status IN (ON_SALE, OFF_SHELF)）不匹配（0 行）→ CONFLICT，
        // 读取时的 status/moderation_status 不被写回（I1/I2 保持）。
        Item item = new Item();
        item.setId(100L);
        item.setPublisherId(7L);
        item.setSchoolId(2L);
        item.setCategoryId(3L);
        item.setType(ItemType.SELL);
        item.setTitle("旧标题");
        item.setDescription("旧描述");
        item.setPrice(new BigDecimal("20.00"));
        item.setImages(List.of("/uploads/items/test.jpg"));
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(ModerationStatus.PASSED);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(categoryMapper.selectById(3L)).thenReturn(new Category());
        arrangeListingRevision();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of()));
        when(itemMapper.update(any(Item.class), any())).thenReturn(0); // 并发下单抢先迁移

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(7L, 100L, publishRequest()));

        assertEquals(409, error.getCode());
        verify(itemMapper, never()).updateById(any(Item.class));
        verify(itemTagService, never()).replaceTags(any(), any(), any());
    }

    @Test
    void updateFailsWhenModerationFlippedToRejectedConcurrently() {
        // §7.1 "违规确认 vs 编辑" 交错的单元级决胜点：
        // 编辑者无锁读到 ON_SALE/PASSED → 并发违规确认把商品压到 OFF_SHELF+REJECTED
        // （OFF_SHELF 仍在条件 IN 列表内，只有 moderation 重检能拒绝）→
        // 0 行 → CONFLICT，PASSED 不被写回吞掉 REJECTED（I24）。
        Item item = new Item();
        item.setId(100L);
        item.setPublisherId(7L);
        item.setSchoolId(2L);
        item.setCategoryId(3L);
        item.setType(ItemType.SELL);
        item.setTitle("旧标题");
        item.setDescription("旧描述");
        item.setPrice(new BigDecimal("20.00"));
        item.setImages(List.of("/uploads/items/test.jpg"));
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(ModerationStatus.PASSED);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(categoryMapper.selectById(3L)).thenReturn(new Category());
        arrangeListingRevision();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of()));
        when(itemMapper.update(any(Item.class), any())).thenReturn(0); // 并发违规确认抢先投影 REJECTED

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(7L, 100L, publishRequest()));

        assertEquals(409, error.getCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Item>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(itemMapper).update(any(Item.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("moderation_status"),
                () -> "条件 UPDATE 必须重检 moderation_status，实际 WHERE: " + sqlSegment);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(ModerationStatus.PASSED),
                "moderation 重检必须以读取时的值为准");
        verify(itemTagService, never()).replaceTags(any(), any(), any());
    }

    @Test
    void relistFailsWhenModerationFlippedToRejectedConcurrently() {
        // §7.1 "放行 vs 新违规确认" 交错的单元级决胜点（relist 侧）：
        // 读取 OFF_SHELF/PASSED → 并发违规确认投影 REJECTED（status 仍 OFF_SHELF）→
        // moderation 重检 0 行 → CONFLICT，不允许 REJECTED 商品经 relist 回到 ON_SALE+PASSED。
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
        item.setStatus(ItemStatus.OFF_SHELF);
        item.setModerationStatus(ModerationStatus.PASSED);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(userMapper.selectById(7L)).thenReturn(publisher());
        when(categoryMapper.selectById(3L)).thenReturn(new Category());
        arrangeListingRevision();
        when(contentAnalyzer.analyze(any(), any())).thenReturn(new LocalContentAnalyzer.AnalysisResult(
                false, "", List.of(), "2026.1", List.of()));
        when(itemTagService.tagsByItemIds(any())).thenReturn(Map.of());
        when(contentAnalyzer.sanitizeUserTags(any())).thenReturn(new LocalContentAnalyzer.TagCheck(
                List.of(), false, "", List.of()));
        when(itemMapper.update(any(Item.class), any())).thenReturn(0); // 并发违规确认抢先投影 REJECTED

        BusinessException error = assertThrows(BusinessException.class, () -> service.relist(7L, 100L));

        assertEquals(409, error.getCode());
        verify(itemTagService, never()).replaceTags(any(), any(), any());
    }

    @Test
    void reservedItemCannotBeModified() {
        Item item = new Item();
        item.setId(100L);
        item.setPublisherId(7L);
        item.setStatus(ItemStatus.RESERVED); // 交易中
        when(itemMapper.selectById(100L)).thenReturn(item);

        assertThrows(BusinessException.class, () -> service.relist(7L, 100L));
        verify(itemMapper, never()).updateById(any(Item.class));
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

    private void arrangeListingRevision() {
        when(itemMapper.bumpListingRevision()).thenReturn(1);
        when(itemMapper.currentListingRevision()).thenReturn(41L);
    }

    private SysUser publisher() {
        SysUser publisher = new SysUser();
        publisher.setId(7L);
        publisher.setSchoolId(2L);
        publisher.setCampus("宝山校区");
        publisher.setDormitory("南 8");
        return publisher;
    }

    private void arrangePublisherAndCategory() {
        when(userMapper.selectById(7L)).thenReturn(publisher());
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
