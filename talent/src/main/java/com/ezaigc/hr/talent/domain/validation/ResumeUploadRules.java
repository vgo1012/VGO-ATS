package com.ezaigc.hr.talent.domain.validation;

import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_NAME_LENGTH;
import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;

import java.util.Locale;

/** 简历上传的文件名、大小与扩展名规则，供 HTTP 和应用层共同复用。 */
public final class ResumeUploadRules {

  private ResumeUploadRules() {}

  public static boolean isValidSize(long sizeBytes) {
    return sizeBytes > 0 && sizeBytes <= MAX_FILE_SIZE_BYTES;
  }

  /**
   * 提取浏览器可能携带的本地路径末段，并拒绝控制字符和双向格式控制符。
   *
   * @return 安全文件名；输入非法时返回 {@code null}
   */
  public static String normalizeFileName(String value) {
    if (value == null
        || value.length() > MAX_FILE_NAME_LENGTH
        || value.codePoints().anyMatch(ResumeUploadRules::isUnsafeCodePoint)) {
      return null;
    }
    int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
    String fileName = value.substring(separator + 1);
    if (fileName.isBlank()) {
      return null;
    }
    return fileName;
  }

  public static boolean hasSupportedExtension(String fileName) {
    if (fileName == null) return false;
    String normalized = fileName.toLowerCase(Locale.ROOT);
    return normalized.endsWith(".pdf")
        || normalized.endsWith(".doc")
        || normalized.endsWith(".docx");
  }

  private static boolean isUnsafeCodePoint(int codePoint) {
    return Character.isISOControl(codePoint)
        || Character.getType(codePoint) == Character.FORMAT;
  }
}
