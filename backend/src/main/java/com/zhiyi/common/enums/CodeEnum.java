package com.zhiyi.common.enums;

/**
 * 数据库存储稳定代码值的领域枚举。
 */
public interface CodeEnum {
    String getCode();

    default String code() {
        return getCode();
    }
}
