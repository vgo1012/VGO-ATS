package com.ezaigc.hr.common.core.enums;

public interface CodeDescEnum {

    default String getCode() {
        return ((Enum<?>) this).name();
    }

    /**
     * 获取枚举值中文说明。
     */
    String getDesc();

    /**
     * 判断外部或持久化编码是否表示当前枚举值。
     */
    default boolean matches(String code) {
        return getCode().equals(code);
    }
}
