package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.io.IOException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可触发简历整文档抽取降级的错误编码。 */
@Getter
@RequiredArgsConstructor
public enum ResumeExtractionErrorCode implements CodeDescEnum {
  DOCUMENT_PAGE_LIMIT("文档页数超过配置上限"),
  DOCUMENT_TEXT_LIMIT("文档提取文本超过配置上限"),
  DOCUMENT_CONTAINER_LIMIT("文档容器超过安全上限"),
  DOCUMENT_CONTAINER_INVALID("文档容器格式无效"),
  DOCUMENT_PAGE_RENDER_LIMIT("文档单页渲染尺寸超过配置上限"),
  DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE("分页抽取不可用"),
  DOCUMENT_EXTRACTION_FAILED("文档抽取失败");

  private final String desc;

  public static String classify(IOException failure) {
    String message = failure.getMessage();
    if (message == null) return DOCUMENT_EXTRACTION_FAILED.name();
    for (ResumeExtractionErrorCode value : values()) {
      if (message.startsWith(value.name())) return value.name();
    }
    if (message.contains("QUALITY_LOW") || message.contains("DOCUMENT_EMPTY")) {
      return "DOCUMENT_QUALITY_INVALID";
    }
    if (message.contains("exceeds")) return "DOCUMENT_SIZE_INVALID";
    return DOCUMENT_EXTRACTION_FAILED.name();
  }

  public static boolean allowsWholeDocumentFallback(String code) {
    return DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE.matches(code)
        || DOCUMENT_EXTRACTION_FAILED.matches(code);
  }
}
