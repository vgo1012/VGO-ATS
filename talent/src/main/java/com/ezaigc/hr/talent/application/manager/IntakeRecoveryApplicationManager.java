package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import jakarta.annotation.Resource;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 确认响应丢失后的精确恢复读取，不扫描或重新提交整批收件。 */
@Component
public class IntakeRecoveryApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private TalentIntakeAssembler assembler;

  @Transactional(readOnly = true)
  public ResumeIntakeResponse detail(String id) {
    var intake = intakes.findIntake(id);
    if (intake == null) throw PlatformExceptions.notFound("INTAKE_NOT_FOUND", "收件不存在");
    authorization.requireAccess(
        "talent.read",
        new AuthorizationTarget(
            "RESUME_INTAKE",
            id,
            intake.getOwnerUserId(),
            intake.getOwnerDepartmentId(),
            Set.of()));
    return assembler.toResponse(intake);
  }
}
