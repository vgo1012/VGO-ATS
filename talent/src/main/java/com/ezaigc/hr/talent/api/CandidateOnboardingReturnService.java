package com.ezaigc.hr.talent.api;

/** 将确认未入职的候选人当前简历转入共享库。 */
public interface CandidateOnboardingReturnService {
  void returnToShared(
      String candidateId, String applicationResumeVersionId, String reason, String actorUserId);
}
