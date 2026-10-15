package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class TalentIntakeRetryGuardTest {
  @ParameterizedTest
  @ValueSource(strings = {"FAILED_FINAL", "QUEUED", "RUNNING", "SUCCEEDED", "FAILED_RETRYABLE"})
  void scannerRecoveryReusesSharedProcessingAndRestartsOnlyExhaustedFailure(String sharedStatus)
      throws Exception {
    Instant now = Instant.parse("2026-09-12T00:00:00Z");
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
    ResumeIntakeProcessingPersistenceService processing = mock(ResumeIntakeProcessingPersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    MalwareScanPort scanner = mock(MalwareScanPort.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    ResumeIntake intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setStatus("FAILED_RETRYABLE");
    intake.setVersion(2);
    intake.setSourceRecruiterUserId("actor-1");
    intake.setOwnerUserId("actor-1");
    intake.setOwnerDepartmentId("department-1");
    intake.setContentSha256("aabbcc");
    intake.setObjectKey("quarantine/aa/aabbcc");
    intake.setOriginalFileName("resume.pdf");
    intake.setMediaType("application/pdf");
    ResumeIntakeProcessing shared = new ResumeIntakeProcessing();
    shared.setId("shared-processing");
    shared.setStatus(sharedStatus);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    when(authorization.currentActor()).thenReturn(actor);
    when(persistence.findIntake("intake-1")).thenReturn(intake);
    when(transactions.execute(any())).thenAnswer(invocation ->
        invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    when(storage.open("quarantine/aa/aabbcc")).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(scanner.scan(any(), any())).thenReturn(MalwareScanVerdict.CLEAN);
    when(storage.exists("resume/aa/aabbcc")).thenReturn(true);
    when(ids.newId()).thenReturn("proposed-processing", "event-1");
    when(processing.findByHash("aabbcc")).thenReturn(shared);
    when(persistence.updateIntakeStatus("intake-1", "PARSING", null, 2, "actor-1", now)).thenReturn(1);
    when(persistence.attachProcessing("intake-1", "shared-processing", "actor-1", now)).thenReturn(1);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "intakeProcessing", processing);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "storage", storage);
    ReflectionTestUtils.setField(manager, "scanner", scanner);
    ReflectionTestUtils.setField(manager, "transactionTemplate", transactions);
    ReflectionTestUtils.setField(manager, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "assembler", mock(TalentIntakeAssembler.class));

    manager.retry("intake-1", new TalentVersionReasonDTO(2, "扫描恢复后重试", null));

    verify(processing).insertProcessing("proposed-processing", "aabbcc", "resume.pdf",
        "application/pdf", "resume/aa/aabbcc", "actor-1", now);
    verify(persistence).attachProcessing("intake-1", "shared-processing", "actor-1", now);
    if ("FAILED_FINAL".equals(sharedStatus)) {
      verify(processing).retry("shared-processing", "actor-1", now);
    } else {
      verify(processing, never()).retry(any(), any(), any());
    }
  }

  @Test
  void missingReasonStopsBeforeDatabaseAndFileIo() {
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "storage", storage);

    assertThatThrownBy(() -> manager.retry("intake-1", new TalentVersionReasonDTO(2, " ", null)))
        .isInstanceOf(PlatformException.class);

    verify(persistence, never()).findIntake(any());
    verifyNoInteractions(storage);
  }

  @Test
  void staleVersionStopsBeforeFileIo() {
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    TransactionTemplate transactions = mock(TransactionTemplate.class);
    ResumeIntake intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setSourceRecruiterUserId("recruiter");
    intake.setOwnerUserId("owner");
    intake.setOwnerDepartmentId("department");
    intake.setStatus("FAILED_RETRYABLE");
    intake.setVersion(3);
    when(persistence.findIntake("intake-1")).thenReturn(intake);
    when(transactions.execute(any())).thenAnswer(invocation ->
        invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "storage", storage);
    ReflectionTestUtils.setField(manager, "transactionTemplate", transactions);

    assertThatThrownBy(() -> manager.retry("intake-1", new TalentVersionReasonDTO(2, "重试", null)))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code())
                .isEqualTo("VERSION_CONFLICT"));

    verifyNoInteractions(storage);
  }
}
