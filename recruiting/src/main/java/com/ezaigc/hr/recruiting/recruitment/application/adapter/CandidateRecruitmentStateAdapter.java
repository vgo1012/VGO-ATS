package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationDecision;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStage;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.recruiting.submission.service.ClientRestrictionPersistenceService;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CandidateRecruitmentStateAdapter implements CandidateRecruitmentStateQuery {
  @Resource private ApplicationPersistenceService applications;

  @Override
  public boolean hasMergeProtectedHistory(String candidateId) {
    return applications.hasMergeProtectedHistory(candidateId);
  }
  @Resource private ClientRestrictionPersistenceService restrictions;

  @Override
  public boolean hasRecruitmentHistory(String resumeVersionId) {
    return applications.hasRecruitmentHistory(resumeVersionId);
  }

  @Override
  public boolean hasActiveRecruitment(String resumeVersionId) {
    return applications.hasActiveRecruitment(resumeVersionId);
  }

  @Override
  public Map<String, List<State>> states(Collection<String> candidateIds) {
    if (candidateIds.size() > 1000) throw new IllegalArgumentException("候选人批量查询最多1000项");
    Map<String, List<State>> result = new HashMap<>();
    var rows = applications.findForCandidates(List.copyOf(candidateIds));
    var blacklistedApplications = restrictions.applicationsWithBlacklistRecords(rows.stream()
        .map(application -> application.getId()).collect(Collectors.toSet()));
    for (var application : rows) {
      boolean terminated = ApplicationStage.TERMINATED.matches(application.getCurrentStage());
      result
          .computeIfAbsent(application.getCandidateId(), ignored -> new ArrayList<>())
          .add(
              new State(
                  application.getResumeVersionId(),
                  application.getOwnerUserId(),
                  application.getCurrentStage(),
                  !terminated,
                  terminated && ApplicationDecision.REJECT.matches(application.getOutcome()),
                  application.getId(),
                  application.getJdVersionId(),
                  blacklistedApplications.contains(application.getId()),
                  application.getTerminalAt()));
    }
    return result;
  }
}
