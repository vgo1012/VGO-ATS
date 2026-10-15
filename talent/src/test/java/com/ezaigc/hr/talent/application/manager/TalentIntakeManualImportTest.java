package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.ImportIntakeDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TalentIntakeManualImportTest {
  private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");
  private static final String ACTOR_ID = "handler-1";

  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final ResumeIntakeProcessingPersistenceService processing =
      mock(ResumeIntakeProcessingPersistenceService.class);
  private final TalentProcessEventPersistenceService events =
      mock(TalentProcessEventPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final TalentIntakeAssembler assembler = mock(TalentIntakeAssembler.class);
  private final JobContextQuery jobContexts = mock(JobContextQuery.class);
  private final ResumeIntakeResponse response = mock(ResumeIntakeResponse.class);
  private TalentIntakeApplicationManager manager;

  @BeforeEach
  void setUp() {
    manager = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "intakeProcessing", processing);
    ReflectionTestUtils.setField(manager, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "assembler", assembler);
    ReflectionTestUtils.setField(manager, "jobContexts", jobContexts);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    when(authorization.currentActor()).thenReturn(actor());
    when(intakes.findIntake("intake-1")).thenReturn(importable());
    when(ids.newId()).thenReturn("processing-1", "event-1");
    when(intakes.updateIntakeStatus("intake-1", "PARSING", null, 3, ACTOR_ID, NOW)).thenReturn(1);
    when(intakes.attachProcessing("intake-1", "processing-1", ACTOR_ID, NOW)).thenReturn(1);
    when(processing.insertProcessing("processing-1", "sha", "resume.pdf", "application/pdf",
        "resume/key", ACTOR_ID, NOW)).thenReturn(1);
    when(processing.findByHash("sha")).thenReturn(shared("QUEUED"));
    when(assembler.toResponse(any(ResumeIntake.class))).thenReturn(response);
    when(jobContexts.visibleVersions(any())).thenReturn(Map.of());
  }

  @Test
  void startsParsingAndAuditsActualHandler() {
    assertThat(manager.importIntake("intake-1", new ImportIntakeDTO(3))).isSameAs(response);

    verify(intakes).updateIntakeStatus("intake-1", "PARSING", null, 3, ACTOR_ID, NOW);
    verify(processing).insertProcessing("processing-1", "sha", "resume.pdf", "application/pdf",
        "resume/key", ACTOR_ID, NOW);
    verify(intakes).attachProcessing("intake-1", "processing-1", ACTOR_ID, NOW);
    verify(events).insertEvent("event-1", "RESUME_INTAKE_IMPORT_STARTED", ACTOR_ID,
        "RESUME_INTAKE", "intake-1", null, NOW);
  }

  @Test
  void preservesHistoricalDuplicateWhenCreatingFirstSharedProcessing() {
    ResumeIntake intake = importable();
    intake.setDuplicateContent(true);
    when(intakes.findIntake("intake-1")).thenReturn(intake);

    manager.importIntake("intake-1", new ImportIntakeDTO(3));

    verify(assembler).toResponse(argThat(ResumeIntake::isDuplicateContent));
  }

  @Test
  void reusesSharedProcessingAndRestartsOnlyTerminalFailure() {
    when(processing.insertProcessing(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
    when(processing.findByHash("sha")).thenReturn(shared("FAILED_FINAL"));

    manager.importIntake("intake-1", new ImportIntakeDTO(3));

    verify(processing).retry("processing-1", ACTOR_ID, NOW);
    verify(assembler).toResponse(argThat(ResumeIntake::isDuplicateContent));
  }

  @Test
  void duplicateRequestInProgressReturnsWithoutCreatingTask() {
    ResumeIntake intake = importable();
    intake.setStatus("PARSING");
    when(intakes.findIntake("intake-1")).thenReturn(intake);

    assertThat(manager.importIntake("intake-1", new ImportIntakeDTO(2))).isSameAs(response);

    verify(intakes, never()).updateIntakeStatus(any(), any(), any(), anyLong(), any(), any());
    verifyNoInteractions(processing, events);
  }

  @Test
  void staleVersionStopsBeforeTaskCreation() {
    assertThatThrownBy(() -> manager.importIntake("intake-1", new ImportIntakeDTO(2)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));

    verifyNoInteractions(processing, events);
  }

  @Test
  void failedCompareAndSetStopsBeforeTaskCreation() {
    when(intakes.updateIntakeStatus("intake-1", "PARSING", null, 3, ACTOR_ID, NOW)).thenReturn(0);

    assertThatThrownBy(() -> manager.importIntake("intake-1", new ImportIntakeDTO(3)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));

    verifyNoInteractions(processing, events);
  }

  @Test
  void unsafeOrWrongStateCannotStartImport() {
    ResumeIntake intake = importable();
    intake.setStatus("FAILED_RETRYABLE");
    when(intakes.findIntake("intake-1")).thenReturn(intake);

    assertThatThrownBy(() -> manager.importIntake("intake-1", new ImportIntakeDTO(3)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_NOT_IMPORTABLE"));

    verifyNoInteractions(processing, events);
  }

  @Test
  void missingJdCannotStartImportOrCreateSharedProcessing() {
    ResumeIntake intake = importable();
    intake.setJdVersionId(null);
    when(intakes.findIntake("intake-1")).thenReturn(intake);

    assertThatThrownBy(() -> manager.importIntake("intake-1", new ImportIntakeDTO(3)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_JD_VERSION_REQUIRED"));

    verify(intakes, never()).updateIntakeStatus(any(), any(), any(), anyLong(), any(), any());
    verifyNoInteractions(processing, events);
  }

  @Test
  void deniedHandlerCannotStartImport() {
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权入库"))
        .when(authorization).requireAccess(any(), any());

    assertThatThrownBy(() -> manager.importIntake("intake-1", new ImportIntakeDTO(3)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

    verifyNoInteractions(processing, events);
  }

  private static ResumeIntake importable() {
    ResumeIntake intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setSourceRecruiterUserId("source-1");
    intake.setOwnerUserId("owner-1");
    intake.setOwnerDepartmentId("department-1");
    intake.setOriginalFileName("resume.pdf");
    intake.setMediaType("application/pdf");
    intake.setContentSha256("sha");
    intake.setObjectKey("resume/key");
    intake.setJdVersionId("01JJDVERSION00000000000001");
    intake.setScanStatus("CLEAN");
    intake.setStatus("PENDING_IMPORT");
    intake.setVersion(3);
    return intake;
  }

  private static ResumeIntakeProcessing shared(String status) {
    ResumeIntakeProcessing value = new ResumeIntakeProcessing();
    value.setId("processing-1");
    value.setStatus(status);
    return value;
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot(ACTOR_ID, "处理人", "handler@example.com", "department-1",
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("talent.manage"), false, false);
  }
}
