package com.zhiyi.module.user.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.user.dto.UpdateProfileDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.common.enums.SchoolStatus;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.vo.SellerDetailVO;
import com.zhiyi.module.user.vo.UserVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(SysUser.class, SysUserMapper.class);
    }

    /** 真实 SchoolService 包一个 mock 的 SchoolMapper，避免把服务内部行为一并 mock 掉。 */
    private SchoolService schoolServiceReturning(Long schoolId, String name) {
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        if (schoolId != null) {
            School school = new School();
            school.setId(schoolId);
            school.setName(name);
            school.setStatus(SchoolStatus.ACTIVE);
            when(schoolMapper.selectById(schoolId)).thenReturn(school);
        }
        return new SchoolService(schoolMapper);
    }

    private SysUser cardUser() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setNickname("测试同学");
        user.setLevel(3);
        user.setSchoolId(1L);
        user.setPhone("13800138000");
        user.setSchoolEmail("student@shu.edu.cn");
        user.setCampus("宝山校区");
        user.setCollege("计算机学院");
        user.setGrade("2024级");
        user.setDormitory("南区3号楼");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void publicProfileExposesCardFieldsAndSchool() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectOne(any())).thenReturn(cardUser());
        UserService service = new UserService(userMapper, null, schoolServiceReturning(1L, "上海大学"));

        JsonNode json = JsonMapper.builder().build().valueToTree(service.getPublicProfile(42L));
        Set<String> fieldNames = new HashSet<>(json.propertyNames());

        assertEquals(
                Set.of("id", "nickname", "level", "levelTitle", "schoolName"),
                fieldNames);
        assertEquals("上海大学", json.get("schoolName").asString());
    }

    @Test
    void sellerDetailExposesContactAndCampusFields() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser viewer = cardUser();
        viewer.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(viewer);
        when(userMapper.selectOne(any())).thenReturn(cardUser());
        UserService service = new UserService(userMapper, null, schoolServiceReturning(1L, "上海大学"));

        SellerDetailVO detail = service.getSellerDetail(7L, 42L);

        assertEquals(42L, detail.getId());
        assertEquals("测试同学", detail.getNickname());
        assertEquals(3, detail.getLevel());
        assertEquals("上海大学", detail.getSchoolName());
        assertEquals("13800138000", detail.getPhone());
        assertEquals("student@shu.edu.cn", detail.getSchoolEmail());
        assertEquals("宝山校区", detail.getCampus());
        assertEquals("计算机学院", detail.getCollege());
        assertEquals("2024级", detail.getGrade());
        assertEquals("南区3号楼", detail.getDormitory());
    }

    @Test
    void sellerDetailRejectsCrossSchoolViewer() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser viewer = cardUser();
        viewer.setId(7L);
        viewer.setSchoolId(2L);
        when(userMapper.selectById(7L)).thenReturn(viewer);
        when(userMapper.selectOne(any())).thenReturn(cardUser());
        UserService service = new UserService(
                userMapper, null, schoolServiceReturning(1L, "上海大学"));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.getSellerDetail(7L, 42L));
        assertEquals(403, error.getCode());
    }

    @Test
    void updateProfilePersistsTrustFields() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(42L)).thenReturn(cardUser());
        when(userMapper.update(any(SysUser.class), any())).thenReturn(1);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(1L, "上海大学"));

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setCampus("  宝山校区 ");
        dto.setCollege("  计算机学院 ");
        dto.setGrade("2024级");
        dto.setDormitory("南区3号楼");
        service.updateProfile(42L, dto);

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).update(patch.capture(), any());
        assertEquals("宝山校区", patch.getValue().getCampus());
        assertEquals("计算机学院", patch.getValue().getCollege());
        assertEquals("2024级", patch.getValue().getGrade());
        assertEquals("南区3号楼", patch.getValue().getDormitory());
    }

    @Test
    void updateProfileExplicitlyClearsOptionalTrustFields() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(42L)).thenReturn(cardUser());
        when(userMapper.update(any(SysUser.class), any())).thenReturn(1);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(1L, "上海大学"));

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setCampus(" ");
        dto.setCollege(" ");
        dto.setGrade("");
        dto.setDormitory("   ");
        service.updateProfile(42L, dto);

        verify(userMapper).update(any(SysUser.class), argThat(wrapper -> {
            assertTrue(wrapper instanceof LambdaUpdateWrapper<?>);
            String sqlSet = ((LambdaUpdateWrapper<?>) wrapper).getSqlSet();
            assertTrue(sqlSet.contains("campus="));
            assertTrue(sqlSet.contains("college="));
            assertTrue(sqlSet.contains("grade="));
            assertTrue(sqlSet.contains("dormitory="));
            return true;
        }));
    }

    @Test
    void updateProfileAllowsChangingSchoolAndMatchingEmail() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser current = cardUser();
        SysUser updated = cardUser();
        updated.setSchoolId(2L);
        updated.setSchoolEmail("student@dhu.edu.cn");
        when(userMapper.selectById(42L)).thenReturn(current, updated);
        when(userMapper.update(any(SysUser.class), any())).thenReturn(1);

        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        when(schoolMapper.selectById(2L)).thenReturn(school(2L, "东华大学", "@dhu.edu.cn"));
        UserService service = new UserService(userMapper, null, new SchoolService(schoolMapper));

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setSchoolId(2L);
        dto.setSchoolEmail("  STUDENT@DHU.EDU.CN ");
        UserVO result = service.updateProfile(42L, dto);

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).update(patch.capture(), any());
        assertEquals(2L, patch.getValue().getSchoolId());
        assertEquals("student@dhu.edu.cn", patch.getValue().getSchoolEmail());
        assertEquals("东华大学", result.getSchoolName());
        assertEquals("student@dhu.edu.cn", result.getSchoolEmail());
    }

    @Test
    void updateProfileRejectsEmailThatDoesNotMatchNewSchool() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(42L)).thenReturn(cardUser());

        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        when(schoolMapper.selectById(2L)).thenReturn(school(2L, "东华大学", "@dhu.edu.cn"));
        UserService service = new UserService(userMapper, null, new SchoolService(schoolMapper));

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setSchoolId(2L);
        dto.setSchoolEmail("student@shu.edu.cn");

        assertThrows(BusinessException.class, () -> service.updateProfile(42L, dto));
    }

    @Test
    void getProfileFillsSchoolName() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(42L)).thenReturn(cardUser());
        UserService service = new UserService(userMapper, null, schoolServiceReturning(1L, "上海大学"));

        UserVO vo = service.getProfile(42L);
        assertEquals("上海大学", vo.getSchoolName());
    }

    @Test
    void relationTagsCompareOnlyBothNonBlankFields() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(null, null));

        SysUser viewer = new SysUser();
        viewer.setId(1L);
        viewer.setSchoolId(1L);
        viewer.setCampus("宝山校区");
        viewer.setCollege("计算机学院");
        viewer.setGrade("2024级");
        viewer.setDormitory("南区3号楼");
        SysUser target = new SysUser();
        target.setId(2L);
        target.setSchoolId(1L);
        target.setCampus("宝山校区");
        target.setCollege("计算机学院"); // 同学院
        target.setGrade("2023级");       // 不同级
        target.setDormitory(null);        // 卖家未填 → 不比对
        when(userMapper.selectById(1L)).thenReturn(viewer);
        when(userMapper.selectById(2L)).thenReturn(target);

        assertEquals(List.of("同学院", "同校区"), service.getRelationTags(1L, 2L));
    }

    @Test
    void relationTagsDoNotCrossSchoolBoundary() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(null, null));

        SysUser viewer = cardUser();
        viewer.setId(1L);
        viewer.setSchoolId(1L);
        SysUser target = cardUser();
        target.setId(2L);
        target.setSchoolId(2L);
        when(userMapper.selectById(1L)).thenReturn(viewer);
        when(userMapper.selectById(2L)).thenReturn(target);

        assertTrue(service.getRelationTags(1L, 2L).isEmpty());
    }

    @Test
    void relationTagsRejectMissingTarget() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(null, null));
        SysUser viewer = cardUser();
        viewer.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(viewer);
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getRelationTags(1L, 99L));
        assertEquals(com.zhiyi.common.ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void relationTagsEmptyWhenViewingSelf() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(null, null));
        assertTrue(service.getRelationTags(5L, 5L).isEmpty());
    }

    @Test
    void schoolNameNullWhenUserHasNoSchool() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = cardUser();
        user.setSchoolId(null);
        when(userMapper.selectById(42L)).thenReturn(user);
        UserService service = new UserService(userMapper, null, schoolServiceReturning(null, null));

        assertNull(service.getProfile(42L).getSchoolName());
    }

    private School school(Long id, String name, String emailDomain) {
        School school = new School();
        school.setId(id);
        school.setName(name);
        school.setEmailDomain(emailDomain);
        school.setStatus(SchoolStatus.ACTIVE);
        return school;
    }
}
