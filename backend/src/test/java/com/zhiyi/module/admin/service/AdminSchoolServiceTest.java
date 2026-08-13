package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.SchoolStatus;
import com.zhiyi.module.admin.dto.SchoolDTO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSchoolServiceTest {

    @Mock private SchoolMapper schoolMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ItemMapper itemMapper;
    private AdminSchoolService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize("com.zhiyi.module.user.mapper.SchoolMapper", School.class);
        initialize("com.zhiyi.module.user.mapper.SysUserMapper", SysUser.class);
        initialize("com.zhiyi.module.item.mapper.ItemMapper", Item.class);
    }

    private static void initialize(String namespace, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = new AdminSchoolService(schoolMapper, userMapper, itemMapper);
    }

    @Test
    @DisplayName("新增学校会规范化代码并默认启用")
    void createNormalizesCodeAndDefaultsStatus() {
        SchoolDTO dto = dto("测试大学", "  test  ", null);
        when(schoolMapper.selectOne(any())).thenReturn(null);

        service.create(dto);

        ArgumentCaptor<School> school = ArgumentCaptor.forClass(School.class);
        verify(schoolMapper).insert(school.capture());
        assertEquals("TEST", school.getValue().getCode());
        assertEquals(SchoolStatus.ACTIVE, school.getValue().getStatus());
    }

    @Test
    @DisplayName("学校代码唯一性检查与大小写、首尾空白无关")
    void createRejectsCaseInsensitiveDuplicateCode() {
        when(schoolMapper.selectOne(any())).thenReturn(school(1L, "SHU", SchoolStatus.ACTIVE));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(dto("重复学校", " shu ", null)));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("SHU"));
        verify(schoolMapper, never()).insert(any(School.class));
    }

    @Test
    void updateRejectsMissingSchoolBeforeCheckingDuplicates() {
        when(schoolMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(99L, dto("不存在", "NONE", null)));

        assertEquals(404, error.getCode());
        verify(schoolMapper, never()).selectOne(any());
    }

    @Test
    void updateKeepsStatusWhenRequestOmitsIt() {
        School existing = school(3L, "旧名称", SchoolStatus.DISABLED);
        when(schoolMapper.selectById(3L)).thenReturn(existing);
        when(schoolMapper.selectOne(any())).thenReturn(null);

        service.update(3L, dto("新名称", " new ", null));

        assertEquals("新名称", existing.getName());
        assertEquals("NEW", existing.getCode());
        assertEquals(SchoolStatus.DISABLED, existing.getStatus());
        verify(schoolMapper).updateById(existing);
    }

    @Test
    void invalidStatusProducesSemanticBadRequest() {
        when(schoolMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(dto("测试大学", "TEST", "ARCHIVED")));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("ACTIVE 或 DISABLED"));
        verify(schoolMapper, never()).insert(any(School.class));
    }

    @Test
    void deleteRejectsMissingSchool() {
        when(schoolMapper.selectById(9L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(9L));

        assertEquals(404, error.getCode());
        verifyNoInteractions(userMapper, itemMapper);
    }

    @Test
    void deleteRejectsSchoolWithUsersWithoutCheckingItems() {
        when(schoolMapper.selectById(1L)).thenReturn(school(1L, "SHU", SchoolStatus.ACTIVE));
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertTrue(error.getMessage().contains("仍有用户"));
        verifyNoInteractions(itemMapper);
        verify(schoolMapper, never()).deleteById(1L);
    }

    @Test
    void deleteRejectsSchoolWithHistoricalItems() {
        when(schoolMapper.selectById(1L)).thenReturn(school(1L, "SHU", SchoolStatus.ACTIVE));
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertTrue(error.getMessage().contains("商品记录"));
        verify(schoolMapper, never()).deleteById(1L);
    }

    @Test
    void deleteRemovesOnlyAnEmptySchool() {
        when(schoolMapper.selectById(7L)).thenReturn(school(7L, "EMPTY", SchoolStatus.DISABLED));
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);

        service.delete(7L);

        verify(schoolMapper).deleteById(7L);
    }

    private SchoolDTO dto(String name, String code, String status) {
        SchoolDTO dto = new SchoolDTO();
        dto.setName(name);
        dto.setCode(code);
        dto.setStatus(status);
        dto.setEmailDomain("@example.edu.cn");
        return dto;
    }

    private School school(Long id, String code, SchoolStatus status) {
        School school = new School();
        school.setId(id);
        school.setName(code + "大学");
        school.setCode(code);
        school.setStatus(status);
        return school;
    }
}
