package com.ezaigc.hr.talent.api;

/** 人才领取共享简历时复核当前有效联系锁，避免覆盖其他招聘人员的跟进事实。 */
public interface CandidateEngagementClaimQuery {
  boolean claimableBy(String candidateId, String actorUserId);
}
