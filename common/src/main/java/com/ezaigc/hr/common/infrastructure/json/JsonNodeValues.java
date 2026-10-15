package com.ezaigc.hr.common.infrastructure.json;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class JsonNodeValues {

    private JsonNodeValues() {
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = child(node, field);
        return value == null || value.isNull() ? null : value.asString();
    }

    public static Integer integer(JsonNode node, String field) {
        JsonNode value = child(node, field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    public static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = child(node, field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    public static List<String> strings(JsonNode node, String field) {
        JsonNode value = child(node, field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        return value.valueStream().map(JsonNode::asString).toList();
    }

    public static <T> List<T> objects(
            ObjectMapper objectMapper, JsonNode node, String field, Class<T> targetType) {
        JsonNode value = child(node, field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        return value.valueStream().map(item -> objectMapper.treeToValue(item, targetType)).toList();
    }

    private static JsonNode child(JsonNode node, String field) {
        return node == null ? null : node.get(field);
    }
}
