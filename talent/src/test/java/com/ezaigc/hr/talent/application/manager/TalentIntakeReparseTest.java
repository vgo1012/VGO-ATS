package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

class TalentIntakeReparseTest {
  private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
  private final TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final ResumeIntakeProcessingPersistenceService processing = mock(ResumeIntakeProcessingPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  private final ResumeIntake intake = new ResumeIntake();

  @BeforeEach
  void setup() {
    intake.setId("intake-1");
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_REVIEW_REQUIRED");
    intake.setScanStatus("CLEAN");
    intake.setProcessingId("shared-1");
    intake.setContentSha256("a".repeat(64));
    intake.setOriginalFileName("candidate.pdf");
    intake.setMediaType("application/pdf");
    intake.setObjectKey("resume/aa/" + "a".repeat(64));
    intake.setOwnerUserId("actor-1");
    intake.setOwnerDepartmentId("department-1");
    intake.setVersion(7);
    ResumeIntakeProcessing shared = new ResumeIntakeProcessing();
    shared.setId("shared-1");
    shared.setStatus("SUCCEEDED");
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    when(authorization.currentActor()).thenReturn(actor);
    when(intakes.findIntake("intake-1")).thenReturn(intake);
    when(processing.findProcessing("shared-1")).thenReturn(shared);
    when(ids.newId()).thenReturn("reparse-1", "event-1");
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "intakeProcessing", processing);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "assembler", mock(TalentIntakeAssembler.class));
  }

  @Test
  void reparsesOnlySelectedIdentityReviewWithoutResettingSharedSuccess() {
    when(processing.insertReparseProcessing("reparse-1", "a".repeat(64), "candidate.pdf",
        "application/pdf", "resume/aa/" + "a".repeat(64), "actor-1", NOW)).thenReturn(1);
    when(intakes.reparseIntake("intake-1", 7, "shared-1", "reparse-1", "actor-1", NOW))
        .thenReturn(1);

    manager.reparse("intake-1", new TalentVersionReasonDTO(7, "重新解析联系人", null));

    verify(processing).insertReparseProcessing("reparse-1", "a".repeat(64), "candidate.pdf",
        "application/pdf", "resume/aa/" + "a".repeat(64), "actor-1", NOW);
    verify(intakes).reparseIntake("intake-1", 7, "shared-1", "reparse-1", "actor-1", NOW);
    verify(processing, never()).retry(any(), any(), any());
    verify(events).insertEvent("event-1", "RESUME_INTAKE_REPARSE_STARTED", "actor-1",
        "RESUME_INTAKE", "intake-1", "重新解析联系人", NOW);
  }

  @Test
  void staleVersionCannotStartAnotherProcessing() {
    assertThatThrownBy(() -> manager.reparse("intake-1", new TalentVersionReasonDTO(6, "重试", null)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
    verify(processing, never()).insertReparseProcessing(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void failedBindingCannotPublishReparseAudit() {
    when(processing.insertReparseProcessing("reparse-1", "a".repeat(64), "candidate.pdf",
        "application/pdf", "resume/aa/" + "a".repeat(64), "actor-1", NOW)).thenReturn(1);
    assertThatThrownBy(() -> manager.reparse("intake-1", new TalentVersionReasonDTO(7, "重试", null)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
    verify(events, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void reparseUsesSpringWriteTransactionForProcessingBindingAndAudit() throws Exception {
    Method method = TalentIntakeApplicationManager.class.getMethod("reparse", String.class,
        TalentVersionReasonDTO.class);
    var attribute = new AnnotationTransactionAttributeSource().getTransactionAttribute(method,
        TalentIntakeApplicationManager.class);
    assertThat(attribute).isNotNull();
    assertThat(attribute.isReadOnly()).isFalse();
  }

  @Test
  void identityConflictCannotUseContactRecovery() {
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    assertThatThrownBy(() -> manager.reparse("intake-1", new TalentVersionReasonDTO(7, "重试", null)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_NOT_REPARSABLE"));
    verify(processing, never()).insertReparseProcessing(any(), any(), any(), any(), any(), any(), any());
  }
}
