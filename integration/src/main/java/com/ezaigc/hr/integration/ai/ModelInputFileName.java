package com.ezaigc.hr.integration.ai;

/** 模型辅助文件名只保留 basename，避免把本地目录和控制字符发往供应商。 */
final class ModelInputFileName {
  private ModelInputFileName() {}

  static String sanitize(String value) {
    if (value == null) return "";
    String name = value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    name = name.replaceAll("[\\p{Cc}\\p{Cf}]", " ");
    // 保留既有按 UTF-16 单元截取的 255 长度上限。
    return name.substring(0, Math.min(name.length(), 255));
  }
}
