package com.ezaigc.hr.common.infrastructure.json;

import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * 将审计快照等内部结构安全序列化为 JSON 文本。
 */
public final class JsonText {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonText() {
    }

    /**
     * 序列化任意仅包含 Jackson 可识别值的对象。
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("value cannot be serialized as JSON", exception);
        }
    }

    /**
     * 序列化单字段对象，保留值的实际 JSON 类型。
     */
    public static String object(String key, Object value) {
        return write(Map.of(key, value));
    }
}
