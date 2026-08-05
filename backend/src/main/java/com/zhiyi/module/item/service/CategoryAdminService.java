package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.item.dto.CategoryDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryAdminService {

    private final CategoryMapper categoryMapper;

    public List<Category> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }

    @Transactional
    public Category create(CategoryDTO dto) {
        String name = dto.getName().trim();
        ensureNameAvailable(name, null);
        Category category = new Category();
        apply(category, dto, name);
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public Category update(Long id, CategoryDTO dto) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        String name = dto.getName().trim();
        ensureNameAvailable(name, id);
        apply(category, dto, name);
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    public void delete(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        try {
            categoryMapper.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ResultCode.CONFLICT, "该分类下已有商品，暂不能删除");
        }
    }

    private void ensureNameAvailable(String name, Long excludedId) {
        LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .ne(excludedId != null, Category::getId, excludedId);
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "分类名称已存在");
        }
    }

    private void apply(Category category, CategoryDTO dto, String name) {
        category.setName(name);
        category.setIcon(StringUtils.hasText(dto.getIcon()) ? dto.getIcon().trim() : "📦");
        category.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }
}
