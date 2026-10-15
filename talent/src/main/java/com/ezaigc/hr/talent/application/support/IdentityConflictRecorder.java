package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityConflictRecorder {

  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Resource private IdentityConflictPersistenceService identityConflictPersistenceService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String record(String intakeId, String candidateA, String candidateB, String type, long intakeVersion) {
    Stream.of(candidateA, candidateB).filter(Objects::nonNull).distinct().sorted()
        .forEach(identityConflictPersistenceService::lockCandidateForConflict);
    var intake = identityConflictPersistenceService.lockIntake(intakeId);
    if (intake == null || intake.getVersion() != intakeVersion
        || (!ResumeIntakeStatus.READY_FOR_CONFIRMATION.matches(intake.getStatus())
            && !ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.matches(intake.getStatus()))) {
      throw PlatformExceptions.versionConflict();
    }
    IdentityConflict existing = identityConflictPersistenceService.findOpenConflictForIntake(intakeId);
    if (existing != null) {
      return existing.getId();
    }
    String id = ids.newId();
    identityConflictPersistenceService.insertConflict(id, intakeId, candidateA, candidateB, type, clock.instant());
    return id;
  }
}
