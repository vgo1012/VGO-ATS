package com.ezaigc.hr.talent.api;

import java.time.Instant;

/** 智能筛选接收人才流转完成通知，结束精确简历版本的待处理状态。 */
public interface CandidateScreeningTransitionService {
  void complete(String candidateId, String resumeVersionId, String actorId, Instant now);
}
