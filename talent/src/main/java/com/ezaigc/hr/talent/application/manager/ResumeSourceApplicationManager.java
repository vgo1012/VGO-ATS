package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.response.ResumeSourceResponse;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeSourceApplicationManager {
  @Resource private TalentIntakeApplicationManager talent;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private SourceChannelPersistenceService channels;
  @Resource private TalentIntakeAssembler assembler;

  @Transactional(readOnly = true)
  public ResumeSourceResponse source(String candidateId, String resumeVersionId) {
    String candidate = RequiredIdentifier.require(candidateId, "candidateId");
    String versionId = RequiredIdentifier.require(resumeVersionId, "resumeVersionId");
    // Application 协作只允许读取该流程绑定的简历版本，不会放开候选人的其他版本。
    talent.candidateResume(candidate, versionId);
    var version = intakes.findCandidateResumeVersion(candidate, versionId);
    if (version == null) {
      throw PlatformExceptions.notFound("RESUME_VERSION_NOT_FOUND", "该候选人的简历版本不存在");
    }
    var intake = intakes.findIntake(version.getIntakeId());
    if (intake == null) {
      throw PlatformExceptions.notFound("RESUME_SOURCE_NOT_FOUND", "该简历版本的来源记录不存在");
    }
    var channel = channels.findChannel(intake.getSourceChannelId());
    return assembler.toSourceResponse(intake, channel);
  }
}
