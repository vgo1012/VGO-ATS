package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.api.CandidateOnboardingReturnService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 入职事实被确认无效后，把候选人的当前简历投影到共享库。 */
@Component
public class OnboardingReturnApplicationManager implements CandidateOnboardingReturnService {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Resource private CandidatePersistenceService candidates;
  @Resource private TalentIntakePersistenceService resumes;
  @Resource private CandidateResumeVersionService routing;
  @Resource private ResumeLifecycleApplicationManager lifecycle;
  @Resource private TalentProcessEventPersistenceService events;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Override
  @Transactional
  public void returnToShared(
      String candidateId, String applicationResumeVersionId, String reason, String actorUserId) {
    candidates.lockCandidate(candidateId);
    var candidate = candidates.findCandidate(candidateId);
    if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
      throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在或已停用");
    }
    var currentResumes = resumes.findCurrentCandidateResumes(List.of(candidateId));
    if (currentResumes.isEmpty()) {
      throw PlatformExceptions.conflict("CURRENT_RESUME_REQUIRED", "候选人缺少当前简历，不能退回共享简历库");
    }
    String resumeVersionId = currentResumes.getFirst().getResumeVersionId();
    routing.lockForRouting(candidateId, resumeVersionId);
    var now = clock.instant();
    ObjectNode ext = metadata(candidate.getExt());
    ext.put("screenedOutResumeVersionId", resumeVersionId);
    ext.put("screenedOutByUserId", actorUserId);
    ext.put("screenedOutReason", reason);
    ext.put("screenedOutAt", now.toString());
    ext.put("onboardingReturnApplicationResumeVersionId", applicationResumeVersionId);
    if (candidates.markReturnedToShared(
            candidateId, candidate.getVersion(), ext.toString(), actorUserId, now)
        != 1) {
      throw PlatformExceptions.conflict("RESOURCE_VERSION_CONFLICT", "候选人资料已更新，请刷新后重新退回");
    }
    events.insertEvent(
        ids.newId(),
        "ONBOARDING_RETURNED_TO_SHARED_LIBRARY",
        actorUserId,
        "CANDIDATE",
        candidateId,
        reason,
        now);
    lifecycle.routed(candidateId, resumeVersionId, "ONBOARDING_RETURN");
  }

  private static ObjectNode metadata(String ext) {
    if (ext == null || ext.isBlank()) return JSON.createObjectNode();
    try {
      return (ObjectNode) JSON.readTree(ext);
    } catch (Exception exception) {
      throw new IllegalStateException("候选人扩展字段格式错误", exception);
    }
  }
}
