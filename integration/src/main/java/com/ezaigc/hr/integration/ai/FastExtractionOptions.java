package com.ezaigc.hr.integration.ai;

import java.util.Locale;
import java.util.Map;
import org.springframework.ai.openai.OpenAiChatOptions;

/** 千问文本和扫描页抽取显式关闭思考；供应商扩展不发送给其他模型。 */
final class FastExtractionOptions {
  private FastExtractionOptions() {}

  static OpenAiChatOptions.Builder forModel(String model) {
    var options = OpenAiChatOptions.builder().temperature(0.0);
    if (model != null && model.toLowerCase(Locale.ROOT).startsWith("qwen")) {
      options.extraBody(Map.of("enable_thinking", false));
    }
    return options;
  }

  /** 当前百炼 OpenAI 兼容接口明确支持原生严格 JSON Schema 的千问系列。 */
  static boolean supportsNativeJsonSchema(String model) {
    if (model == null) return false;
    String normalized = model.toLowerCase(Locale.ROOT);
    return normalized.startsWith("qwen3.7-") || normalized.startsWith("qwen3.8-");
  }
}
