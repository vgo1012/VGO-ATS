package com.ezaigc.hr.talent.domain.dto;

import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 随共享解析结果冻结的原始抽取与模型运行元数据，正式入库时直接复用。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntakeParsedSourceDTO {
  private String extractorVersion;
  private String route;
  private int pageCount;
  private int successfulPages;
  private int fallbackPages;
  private double printableRatio;
  private double coverageRatio;
  private List<Page> pages;
  private String schemaVersion;
  private String promptVersion;
  private int inputTokens;
  private int outputTokens;
  private long durationMillis;
  /** 共享年龄事实计算时的日期；旧归档未记录时为空。 */
  private LocalDate ageAsOf;
  /** 仅首次已绑定 JD 的收件存在；事实落库后导入评分缓存，避免第二次模型调用。 */
  private PrecomputedAssessment precomputedAssessment;
  /** 联系方式经独立解析复核且被采纳时，指向该次加密原始输出。 */
  private String contactRecheckArchiveKey;

  public IntakeParsedSourceDTO(String extractorVersion, String route, int pageCount,
      int successfulPages, int fallbackPages, double printableRatio, double coverageRatio,
      List<Page> pages, String schemaVersion, String promptVersion,
      int inputTokens, int outputTokens, long durationMillis) {
    this(extractorVersion, route, pageCount, successfulPages, fallbackPages, printableRatio,
        coverageRatio, pages, schemaVersion, promptVersion, inputTokens, outputTokens, durationMillis,
        null, null, null);
  }

  public IntakeParsedSourceDTO(String extractorVersion, String route, int pageCount,
      int successfulPages, int fallbackPages, double printableRatio, double coverageRatio,
      List<Page> pages, String schemaVersion, String promptVersion,
      int inputTokens, int outputTokens, long durationMillis, LocalDate ageAsOf) {
    this(extractorVersion, route, pageCount, successfulPages, fallbackPages, printableRatio,
        coverageRatio, pages, schemaVersion, promptVersion, inputTokens, outputTokens,
        durationMillis, ageAsOf, null, null);
  }

  public IntakeParsedSourceDTO(String extractorVersion, String route, int pageCount,
      int successfulPages, int fallbackPages, double printableRatio, double coverageRatio,
      List<Page> pages, String schemaVersion, String promptVersion,
      int inputTokens, int outputTokens, long durationMillis, LocalDate ageAsOf,
      PrecomputedAssessment precomputedAssessment) {
    this(extractorVersion, route, pageCount, successfulPages, fallbackPages, printableRatio,
        coverageRatio, pages, schemaVersion, promptVersion, inputTokens, outputTokens,
        durationMillis, ageAsOf, precomputedAssessment, null);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Page {
    private int pageNo;
    private String route;
    private String text;
    private int characterCount;
    private double printableRatio;
    private double qualityScore;
    private String contentHash;
  }
}
