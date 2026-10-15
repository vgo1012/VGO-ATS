package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import com.ezaigc.hr.recruiting.recruitment.enums.EngagementLockStatus;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.talent.api.CandidateEngagementClaimQuery;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class CandidateEngagementClaimAdapter implements CandidateEngagementClaimQuery {
  @Resource private EngagementLockPersistenceService locks;
  @Resource private Clock clock;

  @Override
  public boolean claimableBy(String candidateId, String actorUserId) {
    var lock = locks.findByCandidate(candidateId);
    return lock == null
        || !EngagementLockStatus.ACTIVE.matches(lock.getStatus())
        || !lock.getExpiresAt().isAfter(clock.instant())
        || actorUserId.equals(lock.getOwnerUserId());
  }
}
