package com.ezaigc.hr.talent.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 从当前领取事实或旧领取审计中确定本轮评分绑定。 */
@Component
public class SharedReclaimScoreApplicationManager {
  private static final ObjectMapper JSON = new ObjectMapper();
  @Resource private TalentProcessEventPersistenceService events;

  public String claimedBindingId(Candidate candidate, List<ResumeJdScoreBinding> bindings) {
    JsonNode ext = JSON.readTree(candidate.getExt() == null || candidate.getExt().isBlank()
        ? "{}" : candidate.getExt());
    String direct = ext.path("reclaimedScoreBindingId").asString();
    if (direct != null && !direct.isBlank()) return direct;

    String resumeVersionId = ext.path("reclaimedResumeVersionId").asString();
    String reclaimedTime = ext.path("reclaimedAt").asString();
    if (resumeVersionId == null || resumeVersionId.isBlank()
        || reclaimedTime == null || reclaimedTime.isBlank() || bindings == null || bindings.isEmpty()) return null;
    Instant reclaimedAt;
    try {
      reclaimedAt = Instant.parse(reclaimedTime);
    } catch (DateTimeParseException exception) {
      return null;
    }
    TalentProcessEvent event = events.getOne(Wrappers.lambdaQuery(TalentProcessEvent.class)
        .eq(TalentProcessEvent::getTargetType, "CANDIDATE")
        .eq(TalentProcessEvent::getTargetId, candidate.getId())
        .eq(TalentProcessEvent::getEventType, "SHARED_CANDIDATE_RECLAIMED")
        .le(TalentProcessEvent::getOccurredAt, reclaimedAt)
        .orderByDesc(TalentProcessEvent::getOccurredAt)
        .orderByDesc(TalentProcessEvent::getId)
        .last("LIMIT 1"));
    if (event == null || event.getExt() == null || event.getExt().isBlank()) return null;
    JsonNode audit = JSON.readTree(event.getExt());
    String auditResume = audit.path("resumeVersionId").asString();
    String auditOwner = audit.path("toOwnerUserId").asString();
    String auditJd = audit.path("jdVersionId").asString();
    String auditRun = audit.path("defaultMatchRunId").asString();
    if (auditResume == null || auditResume.isBlank() || auditOwner == null || auditOwner.isBlank()
        || auditJd == null || auditJd.isBlank() || auditRun == null || auditRun.isBlank()
        || !Objects.equals(resumeVersionId, auditResume)
        || !Objects.equals(candidate.getOwnerUserId(), auditOwner)) return null;
    return bindings.stream()
        .filter(binding -> Objects.equals(candidate.getId(), binding.getCandidateId())
            && Objects.equals(resumeVersionId, binding.getResumeVersionId())
            && Objects.equals(candidate.getOwnerUserId(), binding.getSelectedByUserId())
            && Objects.equals(auditJd, binding.getJdVersionId())
            && Objects.equals(auditRun, binding.getDefaultMatchRunId())
            && binding.getId() != null && !binding.getId().isBlank())
        .map(ResumeJdScoreBinding::getId)
        .findFirst().orElse(null);
  }
}
