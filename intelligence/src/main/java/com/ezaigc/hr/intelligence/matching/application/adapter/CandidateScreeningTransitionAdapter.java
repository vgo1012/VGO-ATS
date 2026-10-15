package com.ezaigc.hr.intelligence.matching.application.adapter;

import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CandidateScreeningTransitionAdapter implements CandidateScreeningTransitionService {
  @Resource private ScreeningAdmissionPersistenceService admissions;

  @Override
  public void complete(String candidateId, String resumeVersionId, String actorId, Instant now) {
    admissions.completeActive(candidateId, resumeVersionId, actorId, now);
  }
}
