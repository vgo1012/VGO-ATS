package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IntakeRecoveryApplicationManagerTest {
  @Test
  void readsExactLatestIntakeAndEnforcesObjectAccess() {
    var manager = new IntakeRecoveryApplicationManager();
    var auth = mock(AuthorizationService.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var row = new ResumeIntake();
    row.setId("intake");
    row.setSourceRecruiterUserId("original-uploader");
    row.setOwnerUserId("new-owner");
    row.setOwnerDepartmentId("dept");
    row.setStatus("CONFIRMED");
    row.setVersion(2);
    row.setJdVersionId("job");
    when(intakes.findIntake("intake")).thenReturn(row);
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
    var result = manager.detail("intake");
    assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    assertThat(result.getVersion()).isEqualTo("2");
    assertThat(result.getJdVersionId()).isEqualTo("job");
    verify(auth)
        .requireAccess(
            eq("talent.read"),
            argThat(
                target ->
                    "intake".equals(target.getObjectId())
                        && "new-owner".equals(target.getOwnerUserId())
                        && target.getCollaboratorUserIds().isEmpty()));
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "收件不可见"))
        .when(auth)
        .requireAccess(eq("talent.read"), any());
    assertThatThrownBy(() -> manager.detail("intake")).hasMessageContaining("不可见");
    assertThatThrownBy(() -> manager.detail("missing")).hasMessageContaining("不存在");
  }
}
