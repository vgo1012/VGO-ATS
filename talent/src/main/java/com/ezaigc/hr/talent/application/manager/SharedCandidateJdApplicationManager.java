package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateMatchingStateQuery;
import com.ezaigc.hr.talent.api.SharedCandidateJdQuery;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedCandidateJdApplicationManager implements SharedCandidateJdQuery {
  @Resource private TalentIntakePersistenceService persistence;
  @Resource @Lazy private CandidateLibraryQueryService library;
  @Resource @Lazy private CandidateMatchingStateQuery matching;

  @Override
  @Transactional(readOnly = true)
  public boolean hasSharedCandidate(String jdVersionId) {
    String afterId = null;
    while (true) {
      var ids = persistence.findCandidateIdsForJdVersion(jdVersionId, afterId, 200);
      if (ids.isEmpty()) break;
      if (!library.eligibleCandidateIds(ids).isEmpty()) return true;
      if (ids.size() < 200) break;
      afterId = ids.getLast();
    }
    afterId = null;
    while (true) {
      var ids = matching.candidateIdsForJdVersion(jdVersionId, afterId, 200);
      if (ids.isEmpty()) return false;
      if (!library.eligibleCandidateIds(ids).isEmpty()) return true;
      if (ids.size() < 200) return false;
      afterId = ids.getLast();
    }
  }
}
