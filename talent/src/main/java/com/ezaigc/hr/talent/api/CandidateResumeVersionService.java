package com.ezaigc.hr.talent.api;

import lombok.Value;
import lombok.experimental.Accessors;

public interface CandidateResumeVersionService {
  ResumeVersionRef requireAvailable(String candidateId, String resumeVersionId);

  /** 在调用方事务中锁定版本，串行裁决准入、退回和进入招聘流程。 */
  void lockForRouting(String candidateId, String resumeVersionId);

  /** 再次招聘或共享领取后，只允许本次领取绑定的成功 JD 评分进入筛选和新流程。 */
  void requireCurrentScore(String candidateId, String resumeVersionId, String jdVersionId, String matchRunId);

  @Value
  @Accessors(fluent = true)
  class ResumeVersionRef {
    String resumeVersionId;
    String candidateId;
  }
}
