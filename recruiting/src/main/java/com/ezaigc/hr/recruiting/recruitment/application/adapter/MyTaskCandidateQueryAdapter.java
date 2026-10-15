package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.recruiting.recruitment.api.MyTaskCandidateQuery;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MyTaskCandidateQueryAdapter implements MyTaskCandidateQuery {
  @Resource private AuthorizationService authorization;
  @Resource private ApplicationPersistenceService applications;

  @Override
  public Set<String> candidateIds(Collection<String> candidateIds, String jdId) {
    authorization.requirePermission("talent.read");
    authorization.requirePermission("recruitment.manage");
    if (candidateIds == null || candidateIds.isEmpty()) return Set.of();
    if (candidateIds.size() > 1000) throw new IllegalArgumentException("候选人批量查询最多1000项");
    return Set.copyOf(
        applications.findMyTaskCandidateIds(
            authorization.currentActor().getUserId(), List.copyOf(candidateIds), jdId));
  }
}
