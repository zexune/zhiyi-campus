package com.zhiyi.module.item.vo;

import java.util.List;

public record TagGroupVO(Long categoryId, String categoryName, List<TagCountVO> tags) {
}
