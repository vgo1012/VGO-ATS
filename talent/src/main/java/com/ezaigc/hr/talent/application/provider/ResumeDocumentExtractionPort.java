package com.ezaigc.hr.talent.application.provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/** 简历文档抽取模块能力端口。 */
public interface ResumeDocumentExtractionPort {
  ExtractedDocument extract(String fileName, InputStream content) throws IOException;

  @Value
  @Accessors(fluent = true)
  class ExtractedDocument {
    String extractorVersion;
    String route;
    int pageCount;
    /** 通过质量门禁的总页数，包含通过 OCR 或多模态识别成功的页。 */
    int successfulPages;
    /** 成功页中使用 OCR 或多模态回退的子集，不能与成功页数相加。 */
    int fallbackPages;
    double printableRatio;
    double coverageRatio;
    @ToString.Exclude List<ExtractedPage> pages;

    public ExtractedDocument(
        String extractorVersion,
        String route,
        int pageCount,
        int successfulPages,
        int fallbackPages,
        double printableRatio,
        double coverageRatio,
        List<ExtractedPage> pages) {
      this.extractorVersion = extractorVersion;
      this.route = route;
      this.pageCount = pageCount;
      this.successfulPages = successfulPages;
      this.fallbackPages = fallbackPages;
      this.printableRatio = printableRatio;
      this.coverageRatio = coverageRatio;
      this.pages = List.copyOf(pages);
    }
  }

  @Value
  @Accessors(fluent = true)
  class ExtractedPage {
    int pageNo;
    String route;
    @ToString.Exclude String text;
    int characterCount;
    double printableRatio;
    double qualityScore;
    String contentHash;
  }
}
