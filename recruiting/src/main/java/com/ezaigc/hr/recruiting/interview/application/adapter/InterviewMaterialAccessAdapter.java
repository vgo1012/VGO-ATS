package com.ezaigc.hr.recruiting.interview.application.adapter;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.InterviewMaterialAccessService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewMaterialAccessAdapter implements InterviewMaterialAccessService {
  @Resource private InterviewApplicationManager interviews;
  @Resource private ApplicationPersistenceService applications;
  @Resource private JdVersionPersistenceService jdVersions;
  @Resource private JdEntityPersistenceService jds;

  @Override
  @Transactional(readOnly = true)
  public InterviewMaterialAccess requireMaterialAccess(String interviewId) {
    // 先按当前参与人及授权范围鉴权，不因知道候选人或 JD 编号而放宽访问。
    var interview = interviews.get(interviewId).getInterview();
    var application = applications.findApplication(interview.getApplicationId());
    var jd = jdVersions.findJdVersion(interview.getJdVersionId());
    if (application == null || jd == null
        || !interview.getCandidateId().equals(application.getCandidateId())
        || !interview.getJdVersionId().equals(application.getJdVersionId())
        || !interview.getJdId().equals(jd.getJdId())) {
      throw PlatformExceptions.conflict("INTERVIEW_MATERIAL_MISMATCH", "面试绑定的应聘材料不一致");
    }
    return new InterviewMaterialAccess(interview.getId(), interview.getCandidateId(),
        application.getResumeVersionId(), jd.getJdId(), jd.getId(), jd.getVersionNo(),
        jd.getTitle(), jds.getById(jd.getJdId()).getCode(), jd.getRawText());
  }
}
