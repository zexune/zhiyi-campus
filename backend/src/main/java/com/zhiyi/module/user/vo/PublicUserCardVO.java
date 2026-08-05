package com.zhiyi.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 公开用户名片 —— 供商品详情和聊天展示，只包含允许匿名访问的字段。
 */
@Data
@AllArgsConstructor
public class PublicUserCardVO {
    private Long id;
    private String nickname;
    private Integer level;
    private String levelTitle;
    /** 学校名称（A8：公开名片展示归属校，未绑定为 null） */
    private String schoolName;
}
