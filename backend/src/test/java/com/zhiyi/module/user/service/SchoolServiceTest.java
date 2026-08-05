package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.vo.SchoolVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolMapper schoolMapper;

    private SchoolService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.user.mapper.SchoolMapper");
        TableInfoHelper.initTableInfo(assistant, School.class);
    }

    @BeforeEach
    void setUp() {
        service = new SchoolService(schoolMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listActiveSchoolsOnlyReturnsActiveOnes() {
        when(schoolMapper.selectList(any())).thenReturn(List.of(
                school(1L, "上海大学", "SHU", "@shu.edu.cn"),
                school(2L, "东华大学", "DHU", "@dhu.edu.cn")));

        List<SchoolVO> result = service.listActiveSchools();

        assertEquals(2, result.size());
        assertEquals("上海大学", result.get(0).getName());
        assertEquals("SHU", result.get(0).getCode());
        assertEquals("@shu.edu.cn", result.get(0).getEmailDomain());

        ArgumentCaptor<Wrapper<School>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(schoolMapper).selectList(captor.capture());
        LambdaQueryWrapper<School> wrapper = (LambdaQueryWrapper<School>) captor.getValue();
        String sql = wrapper.getSqlSegment(); // 触发渲染，填充 paramNameValuePairs
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ACTIVE"),
                () -> "query must filter status=ACTIVE, sql=" + sql + ", params=" + wrapper.getParamNameValuePairs());
    }

    @Test
    void getActiveSchoolReturnsNullForMissingId() {
        when(schoolMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getActiveSchool(99L));
    }

    @Test
    void getActiveSchoolReturnsNullForDisabledSchool() {
        School disabled = school(3L, "已停用大学", "OLD", null);
        disabled.setStatus("DISABLED");
        when(schoolMapper.selectById(3L)).thenReturn(disabled);
        assertNull(service.getActiveSchool(3L));
    }

    @Test
    void getActiveSchoolReturnsActiveSchool() {
        when(schoolMapper.selectById(1L)).thenReturn(school(1L, "上海大学", "SHU", "@shu.edu.cn"));

        School result = service.getActiveSchool(1L);

        assertEquals("上海大学", result.getName());
    }

    private School school(Long id, String name, String code, String emailDomain) {
        School s = new School();
        s.setId(id);
        s.setName(name);
        s.setCode(code);
        s.setEmailDomain(emailDomain);
        s.setStatus("ACTIVE");
        return s;
    }
}
