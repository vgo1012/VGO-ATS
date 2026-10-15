package com.ezaigc.hr.talent.application.provider;

import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import java.time.LocalDate;
import java.util.List;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/** 首次收件时一次调用同时抽取简历事实并完成关联 JD 五维专业评分。 */
public interface CombinedIntakeModelPort {

  ModelResult evaluate(Request request);

  @Value
  @Accessors(fluent = true)
  class Request {
    String resumeSchemaVersion;
    String resumePromptVersion;
    String scoringSchemaVersion;
    String scoringPromptVersion;
    LocalDate evaluatedOn;
    @ToString.Exclude String fileName;
    @ToString.Exclude List<PageText> pages;
    @ToString.Exclude String jdFactsJson;

  }

  @Value
  @Accessors(fluent = true)
  class ModelResult {
    String provider;
    String modelVersion;
    @ToString.Exclude String rawJson;
    int inputTokens;
    int outputTokens;
    long durationMillis;
  }
}
