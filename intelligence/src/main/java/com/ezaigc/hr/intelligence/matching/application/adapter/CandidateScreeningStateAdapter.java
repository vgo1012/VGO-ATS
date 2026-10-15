package com.ezaigc.hr.intelligence.matching.application.adapter;

import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CandidateScreeningStateAdapter implements CandidateScreeningStateQuery {
  @Resource private ScreeningAdmissionPersistenceService admissions;

  @Override
  public boolean hasActiveAdmissions(Collection<String> jdVersionIds) {
    return admissions.hasActiveAdmissions(List.copyOf(jdVersionIds));
  }

  @Override
  public boolean hasMergeProtectedHistory(String candidateId) {
    return admissions.hasMergeProtectedHistory(candidateId);
  }

  @Override
  public Set<String> activeResumeVersionIds(Collection<String> candidateIds) {
    if (candidateIds == null || candidateIds.size() > 1000) {
      throw new IllegalArgumentException("候选人批量查询最多1000项");
    }
    return admissions.activeResumeVersionIds(List.copyOf(candidateIds));
  }
}
