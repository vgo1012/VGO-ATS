package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateApplicationAccessQuery;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 私有候选人读取边界：本人、明确共享或明确 Application 所属/协作关系。 */
@Component
public class CandidateReadAccess {
  @Resource private AuthorizationService authorization;
  @Resource private CandidateLibraryQueryService library;
  @Resource private CandidateApplicationAccessQuery applications;

  public CandidateReadDecision evaluate(Collection<Candidate> candidates) {
    if (candidates.isEmpty()) {
      return new CandidateReadDecision(Set.of(), Set.of(), Map.of());
    }
    var actor = authorization.currentActor();
    boolean canReadPrivate = actor.hasPermission("talent.read");
    var ids = candidates.stream().map(Candidate::getId).toList();
    Set<String> fullResume = new LinkedHashSet<>();
    candidates.stream()
        .filter(candidate -> canReadPrivate && (actor.hasCompanyWideScope()
            || actor.getUserId().equals(candidate.getOwnerUserId())
            || actor.getManagedUserIds().contains(candidate.getOwnerUserId())))
        .map(Candidate::getId)
        .forEach(fullResume::add);
    fullResume.addAll(library.eligibleCandidateIds(ids));

    Map<String, Set<String>> applicationVersions = new HashMap<>();
    if (canReadPrivate) {
      applications
          .accessibleResumeVersionIds(ids, actor.getUserId())
          .forEach((candidateId, versionIds) -> applicationVersions.put(candidateId, Set.copyOf(versionIds)));
    }
    Set<String> readable = new LinkedHashSet<>(fullResume);
    readable.addAll(applicationVersions.keySet());
    return new CandidateReadDecision(
        Set.copyOf(readable), Set.copyOf(fullResume), Map.copyOf(applicationVersions));
  }

  public void requireCandidate(Candidate candidate) {
    if (!evaluate(Set.of(candidate)).canReadCandidate(candidate.getId())) {
      throw PlatformExceptions.forbidden("CANDIDATE_ACCESS_FORBIDDEN", "无权查看该候选人");
    }
  }

  public void requireAllResumes(Candidate candidate) {
    if (!evaluate(Set.of(candidate)).canReadAllResumes(candidate.getId())) {
      throw PlatformExceptions.forbidden("CANDIDATE_RESUME_ACCESS_FORBIDDEN", "无权查看该候选人的简历版本列表");
    }
  }

  public void requireResume(Candidate candidate, String resumeVersionId) {
    if (!evaluate(Set.of(candidate)).canReadResume(candidate.getId(), resumeVersionId)) {
      throw PlatformExceptions.forbidden("CANDIDATE_RESUME_ACCESS_FORBIDDEN", "无权查看该简历版本");
    }
  }
}
