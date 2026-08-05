package com.zhiyi.module.item.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.dto.PublishItemDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemPublishServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ViolationReportMapper violationReportMapper;
    @Mock private MarketplaceService marketplaceService;
    @Mock private SysUserMapper userMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private AiReviewService aiReviewService;

    private ItemPublishService service;

    @BeforeEach
    void setUp() {
        service = new ItemPublishService(
                itemMapper,
                categoryMapper,
                violationReportMapper,
                marketplaceService,
                userMapper,
                new ObjectMapper(),
                transactionManager,
                aiReviewService
        );
    }

    @Test
    void copiesPublishersSchoolToNewItem() {
        SysUser publisher = new SysUser();
        publisher.setId(7L);
        publisher.setSchoolId(2L);
        when(userMapper.selectById(7L)).thenReturn(publisher);

        Category category = new Category();
        category.setId(3L);
        category.setName("生活日用");
        when(categoryMapper.selectById(3L)).thenReturn(category);
        when(aiReviewService.review(any(), any()))
                .thenReturn(new AiReviewService.ReviewResult(false, "", List.of("台灯"), false));
        when(itemMapper.insert(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(91L);
            return 1;
        });
        when(marketplaceService.getSnapshot(91L, 7L)).thenReturn(new ItemCardVO());

        service.publish(7L, publishRequest());

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).insert(itemCaptor.capture());
        assertEquals(2L, itemCaptor.getValue().getSchoolId());
    }

    @Test
    void rejectsPublishingWhenUserHasNoSchool() {
        SysUser publisher = new SysUser();
        publisher.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(publisher);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.publish(7L, publishRequest())
        );

        assertEquals("请先设置所属学校", error.getMessage());
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
