package com.zhiyi.module.social.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatUserVO {
    private Long id;
    private String nickname;
    private Integer level;
    private String levelTitle;
}
