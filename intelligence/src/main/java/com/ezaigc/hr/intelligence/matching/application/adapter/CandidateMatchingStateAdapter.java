package com.ezaigc.hr.intelligence.matching.application.adapter;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateMatchingStateQuery;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateMatchingStateAdapter implements CandidateMatchingStateQuery {
  @Resource private AuthorizationService authorization;
  @Resource private ScreeningAdmissionPersistenceService admissions;
  @Resource private WeightedResultPersistenceService results;
  @Resource @Lazy private CandidateLibraryQueryService candidateLibrary;

  @Override
  @Transactional(readOnly = true)
  public List<String> candidateIdsForJdVersion(String jdVersionId, String afterId, int limit) {
    return results.candidateIdsForJdVersion(jdVersionId, afterId, limit);
  }

  @Override
  @Transactional(readOnly = true)
  public List<State> completedStates(Collection<String> ids) {
    if (ids.size() > 1000) throw new IllegalArgumentException("候选人最多1000项");
    var actor = authorization.currentActor();
    if (ids.isEmpty()) return List.of();
    var shared = candidateLibrary.eligibleCandidateIds(ids);
    List<State> states = new ArrayList<>();
    if (!shared.isEmpty()) {
      results.findCandidateMatchingStates(List.copyOf(shared), null).forEach(row -> states.add(
          new State(row.getCandidateId(), row.getResumeVersionId(), row.getJdVersionId(), row.getMatchRunId())));
    }
    var privateIds = ids.stream().filter(id -> !shared.contains(id)).distinct().toList();
    if (!actor.hasPermission("matching.run") || privateIds.isEmpty()) return List.copyOf(states);
    states.addAll(admissions.findScreeningCandidates(
            privateIds,
            actor.getUserId(),
            actor.hasCompanyWideScope(),
            actor.getOperationalManagedUserIds(),
            actor.visibleDepartmentIdsFor("MATCH_RUN"),
            false)
        .stream()
        .filter(row -> row.getAdmissionId() != null)
        .map(
            row ->
                new State(
                    row.getCandidateId(),
                    row.getResumeVersionId(),
                    row.getJdVersionId(),
                    row.getDefaultMatchRunId()))
        .toList());
    return List.copyOf(states);
  }
}
