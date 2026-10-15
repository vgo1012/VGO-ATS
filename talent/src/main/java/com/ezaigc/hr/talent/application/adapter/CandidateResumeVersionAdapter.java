package com.ezaigc.hr.talent.application.adapter;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.ScoreRunCompletionQuery;
import com.ezaigc.hr.talent.application.manager.SharedReclaimScoreApplicationManager;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.ResumeSubmissionQueryService;
import com.ezaigc.hr.talent.enums.ResumeVersionStatus;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.time.Instant;
import tools.jackson.databind.ObjectMapper;

@Service
public class CandidateResumeVersionAdapter
    implements CandidateResumeVersionService, ResumeSubmissionQueryService {

  @Resource private TalentIntakePersistenceService persistence;
  @Resource private ResumeVersionPersistenceService versions;
  @Resource private CandidatePersistenceService candidates;
  @Resource private CandidateRecruitmentStateQuery recruitment;
  @Resource private ResumeJdScoreBindingPersistenceService scoreBindings;
  @Resource private ScoreRunCompletionQuery scoreRuns;
  @Resource private SharedReclaimScoreApplicationManager reclaimedScores;
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForRouting(String candidateId, String resumeVersionId) {
    candidates.lockCandidate(candidateId);
    versions.lockForRouting(resumeVersionId);
    requireAvailable(candidateId, resumeVersionId);
  }

  @Override
  public ResumeVersionRef requireAvailable(String candidateId, String resumeVersionId) {
    var version = requireAvailableVersion(candidateId, resumeVersionId);
    return new ResumeVersionRef(version.getId(), candidateId);
  }

  @Override
  public void requireCurrentScore(String candidateId, String resumeVersionId, String jdVersionId, String matchRunId) {
    var candidate = candidates.findCandidate(candidateId);
    var ext = JSON.readTree(candidate == null || candidate.getExt() == null ? "{}" : candidate.getExt());
    boolean reclaimed = resumeVersionId.equals(ext.path("reclaimedResumeVersionId").asString());
    if (!reclaimed && !recruitment.hasRecruitmentHistory(resumeVersionId)) return;
    String recordedBindingId = ext.path("reclaimedScoreBindingId").asString();
    String bindingId = reclaimed ? reclaimedScores.claimedBindingId(candidate,
        recordedBindingId == null || recordedBindingId.isBlank()
            ? scoreBindings.findForCandidates(List.of(candidateId)) : List.of()) : null;
    var binding = bindingId == null ? null : scoreBindings.getById(bindingId);
    if (candidate == null || binding == null
        || !candidateId.equals(binding.getCandidateId())
        || !resumeVersionId.equals(binding.getResumeVersionId())
        || !jdVersionId.equals(binding.getJdVersionId())
        || (matchRunId != null && !matchRunId.equals(binding.getDefaultMatchRunId()))
        || !Objects.equals(candidate.getOwnerUserId(), binding.getSelectedByUserId())
        || resumeVersionId.equals(ext.path("screenedOutResumeVersionId").asString())
        || binding.getSelectedAt() == null
        || binding.getDefaultMatchRunId() == null
        || !scoreRuns.successfulRunIds(List.of(binding.getDefaultMatchRunId())).contains(binding.getDefaultMatchRunId())) {
      throw scoreRequired();
    }
    String claimedTime = ext.path("reclaimedAt").asString();
    if (claimedTime == null || claimedTime.isBlank()) throw scoreRequired();
    Instant reclaimedAt = Instant.parse(claimedTime);
    if (binding.getSelectedAt().isAfter(reclaimedAt)) throw scoreRequired();
    if (recruitment.states(List.of(candidateId)).getOrDefault(candidateId, List.of()).stream()
        .filter(state -> resumeVersionId.equals(state.getResumeVersionId()) && !state.isBlocksLibrary())
        .anyMatch(state -> state.getTerminalAt() == null || !state.getTerminalAt().isBefore(reclaimedAt)
            || state.getTerminalAt().isAfter(binding.getSelectedAt()))) {
      throw scoreRequired();
    }
  }

  private static RuntimeException scoreRequired() {
    return PlatformExceptions.conflict("CURRENT_RECRUITMENT_SCORE_REQUIRED",
        "原招聘流程已归档，请从共享简历库重新选择 JD、评分并转入后再推荐或筛选");
  }

  @Override
  public ResumeSubmissionSnapshot requireSubmissionSnapshot(
      String candidateId, String resumeVersionId) {
    var version = requireAvailableVersion(candidateId, resumeVersionId);
    return new ResumeSubmissionSnapshot(
        version.getId(), candidateId, version.getOriginalFileName(), version.getContentSha256());
  }

  private ResumeVersion requireAvailableVersion(String candidateId, String resumeVersionId) {
    var version = persistence.findCandidateResumeVersion(candidateId, resumeVersionId);
    if (version == null || !ResumeVersionStatus.AVAILABLE.matches(version.getStatus())) {
      throw PlatformExceptions.unprocessable("RESUME_VERSION_MISMATCH", "简历版本不存在、不可用或不属于该候选人");
    }
    return version;
  }
}
