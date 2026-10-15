package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.CandidateApplicationAccessQuery;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 批量返回用户明确参与的 Application 及其冻结简历版本。 */
@Service
public class CandidateApplicationAccessAdapter implements CandidateApplicationAccessQuery {
  @Resource private ApplicationPersistenceService applications;
  @Resource private ApplicationCollaboratorPersistenceService collaborators;

  @Override
  public Map<String, Set<String>> accessibleResumeVersionIds(
      Collection<String> candidateIds, String actorUserId) {
    if (candidateIds == null || candidateIds.isEmpty()) return Map.of();
    if (candidateIds.size() > 1000) throw new IllegalArgumentException("候选人批量查询最多1000项");
    var rows = applications.findForCandidates(List.copyOf(candidateIds));
    var nonOwnedApplicationIds = rows.stream()
        .filter(row -> !actorUserId.equals(row.getOwnerUserId()))
        .map(row -> row.getId()).toList();
    Set<String> collaboratingApplicationIds = new LinkedHashSet<>();
    if (!nonOwnedApplicationIds.isEmpty()) {
      collaborators.findCollaboratorsByApplicationIds(nonOwnedApplicationIds).stream()
          .filter(row -> actorUserId.equals(row.getUserId()))
          .map(row -> row.getApplicationId())
          .forEach(collaboratingApplicationIds::add);
    }
    Map<String, Set<String>> result = new HashMap<>();
    for (var row : rows) {
      if (actorUserId.equals(row.getOwnerUserId())
          || collaboratingApplicationIds.contains(row.getId())) {
        result
            .computeIfAbsent(row.getCandidateId(), ignored -> new LinkedHashSet<>())
            .add(row.getResumeVersionId());
      }
    }
    return result.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
  }
}
