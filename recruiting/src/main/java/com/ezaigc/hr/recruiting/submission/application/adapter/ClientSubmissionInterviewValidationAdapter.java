package com.ezaigc.hr.recruiting.submission.application.adapter;

import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientSubmissionInterviewValidationAdapter {

  @Resource private RecruitmentApplicationManager applications;
  @Resource private DemandQueryService demands;
  @Resource private ClientSubmissionValidationAdapter submissions;

  /** 校验并获取发送与匹配。 */
  @Transactional
  public void requireSentAndMatching(
      String submissionId, String applicationId, String jdId, String jdVersionId) {
    var application = applications.requireSubmissionContext(applicationId);
    var demand = demands.requireSubmissionContextForJdVersion(application.jdVersionId());
    submissions.requireLockedSentAndMatching(
        submissionId,
        applicationId,
        application.candidateId(),
        demand.clientId(),
        jdId,
        jdVersionId,
        application.resumeVersionId());
  }
}
