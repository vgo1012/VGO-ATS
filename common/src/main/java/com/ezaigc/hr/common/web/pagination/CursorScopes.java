package com.ezaigc.hr.common.web.pagination;

import com.ezaigc.hr.common.core.util.Sha256Utils;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * 为游标生成不泄漏筛选值的稳定查询作用域。
 */
public final class CursorScopes {

    private CursorScopes() {
    }

    public static String scope(String operation, Object... values) {
        Objects.requireNonNull(operation, "operation");
        Object[] canonical = new Object[values.length + 1];
        canonical[0] = operation;
        for (int index = 0; index < values.length; index++) {
            canonical[index + 1] = canonical(values[index]);
        }
        return operation + ":" + Sha256Utils.fingerprintObjects(0x1f, canonical);
    }

    private static String canonical(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> canonical(entry.getKey()) + "=" + canonical(entry.getValue()))
                    .sorted()
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(CursorScopes::canonical)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isArray()) {
            return Arrays.deepToString(new Object[]{value});
        }
        return String.valueOf(value);
    }
}
