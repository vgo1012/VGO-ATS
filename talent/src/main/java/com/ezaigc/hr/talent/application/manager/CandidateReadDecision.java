package com.ezaigc.hr.talent.application.manager;

import java.util.Map;
import java.util.Set;
import lombok.Value;

/** 一次批量授权计算的不可变结果，避免候选人摘要逐行查询。 */
@Value
public class CandidateReadDecision {
  Set<String> readableCandidateIds;
  Set<String> fullResumeCandidateIds;
  Map<String, Set<String>> applicationResumeVersionIds;

  public boolean canReadCandidate(String candidateId) {
    return readableCandidateIds.contains(candidateId);
  }

  public boolean canReadAllResumes(String candidateId) {
    return fullResumeCandidateIds.contains(candidateId);
  }

  public boolean canReadResume(String candidateId, String resumeVersionId) {
    return canReadAllResumes(candidateId)
        || applicationResumeVersionIds.getOrDefault(candidateId, Set.of()).contains(resumeVersionId);
  }
}
