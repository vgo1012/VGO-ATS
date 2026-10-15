package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 身份域账号和组织记录的启停状态。
 */
@Getter
@RequiredArgsConstructor
public enum IdentityActivationStatus implements CodeDescEnum {

    ACTIVE("启用"),
    DISABLED("停用"),
    DELETED("已删除");

    private final String desc;

    public static IdentityActivationStatus fromCode(String code) {
        return IdentityActivationStatus.valueOf(code);
    }

    /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
    public static final class Codes {
        public static final String ACTIVE = "ACTIVE";
        public static final String DISABLED = "DISABLED";
        public static final String DELETED = "DELETED";

        private Codes() {}
    }
}
