package com.ezaigc.hr.talent.api;

import lombok.Value;
import lombok.ToString;
import lombok.experimental.Accessors;

/** 面试域验证当前会话与具体排期后，返回该应聘活动绑定的材料版本。 */
public interface InterviewMaterialAccessService {
  InterviewMaterialAccess requireMaterialAccess(String interviewId);

  @Value
  @Accessors(fluent = true)
  class InterviewMaterialAccess {
    String interviewId;
    String candidateId;
    String resumeVersionId;
    String jdId;
    String jdVersionId;
    int jdVersionNo;
    String jdTitle;
    String jdCode;
    @ToString.Exclude String jdRawText;
  }
}
