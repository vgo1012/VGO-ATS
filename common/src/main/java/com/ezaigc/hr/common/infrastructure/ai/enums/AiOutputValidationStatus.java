package com.ezaigc.hr.common.infrastructure.ai.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型结构化输出的技术校验状态。
 */
@Getter
@RequiredArgsConstructor
public enum AiOutputValidationStatus implements CodeDescEnum {
    PENDING(Codes.PENDING, "待校验"),
    VALID(Codes.VALID, "校验通过"),
    SCHEMA_INVALID(Codes.SCHEMA_INVALID, "结构无效"),
    EVIDENCE_INVALID(Codes.EVIDENCE_INVALID, "证据无效"),
    MODEL_UNAVAILABLE(Codes.MODEL_UNAVAILABLE, "模型不可用");

    private final String code;
    private final String desc;

    /**
     * 仅供 Java 注解 SQL 使用的编译期编码。
     */
    public static final class Codes {

        public static final String PENDING = "PENDING";
        public static final String VALID = "VALID";
        public static final String SCHEMA_INVALID = "SCHEMA_INVALID";
        public static final String EVIDENCE_INVALID = "EVIDENCE_INVALID";
        public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";

        private Codes() {
        }
    }

    public static AiOutputValidationStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.matches(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知 AI 输出校验状态编码: " + code));
    }
}
