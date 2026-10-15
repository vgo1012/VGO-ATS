package com.ezaigc.hr.common.infrastructure.ai;

import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * 模型输出的跨领域安全守卫，只提供无业务归属且可稳定复用的结构检查。
 */
public final class ModelOutputGuard {

    private ModelOutputGuard() {
    }

    /**
     * 检查任意深度的 JSON 属性名是否包含 {@code score}。属性值中的普通文本不参与判断，避免把简历或 JD 正文中的“score”误判为模型越权评分字段。
     */
    public static boolean containsScoreProperty(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (name.toLowerCase(Locale.ROOT).contains("score")
                        || containsScoreProperty(node.get(name))) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsScoreProperty(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
