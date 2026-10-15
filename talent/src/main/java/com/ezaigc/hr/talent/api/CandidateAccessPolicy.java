package com.ezaigc.hr.talent.api;

import java.util.Map;
import java.util.Set;

/** 面向跨模块命令的候选人对象读取边界。 */
public interface CandidateAccessPolicy {
  void requireReadable(String candidateId);

  /** 要求具备候选人全部简历读取权限，适用于跨归属周期的完整历史视图。 */
  void requireAllResumesReadable(String candidateId);

  void requireResumeReadable(String candidateId, String resumeVersionId);

  Set<String> readableResumeVersionIds(Map<String, String> resumeVersionToCandidate);
}
