package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.*;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.CreateIntakeBatchDTO;
import com.ezaigc.hr.talent.domain.dto.LinkIntakeBatchJdDTO;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.enums.IntakeBatchStatus;
import com.ezaigc.hr.talent.service.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IntakeBatchJobContextTest {
  private static final Instant NOW = Instant.parse("2026-09-10T08:00:00Z");
  private static final String JD_VERSION_1 = "01JJDVERSION00000000000001";
  private static final String JD_VERSION_2 = "01JJDVERSION00000000000002";

  final TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
  final AuthorizationService auth = mock(AuthorizationService.class);
  final JobContextQuery jobs = mock(JobContextQuery.class);
  final IntakeBatchPersistenceService batches = mock(IntakeBatchPersistenceService.class);
  final TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
  final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  final SourceChannel channel = new SourceChannel();
  final SourceChannelPersistenceService channels = mock(SourceChannelPersistenceService.class);
  final TalentIdempotencyPersistenceService idempotency =
      mock(TalentIdempotencyPersistenceService.class);

  @BeforeEach
  void setup() {
    when(auth.currentActor())
        .thenReturn(
            new CurrentActorSnapshot(
                "hr",
                "顾问",
                "hr@example.invalid",
                "department",
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("talent.manage"),
                true,
                false));
    channel.setId("channel");
    channel.setStatus("ACTIVE");
    channel.setCreateId("hr");
    when(channels.findChannel("channel")).thenReturn(channel);
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("batch");
    when(persistence.insertIdempotency(
            anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(batches.insertBatch(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            nullable(String.class),
            any()))
        .thenAnswer(
            call -> {
              var row = new IntakeBatch();
              row.setId("batch");
              row.setJdVersionId(call.getArgument(5));
              when(batches.findBatch("batch")).thenReturn(row);
              return 1;
            });
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "sourceChannelPersistenceService", channels);
    ReflectionTestUtils.setField(manager, "intakeBatchPersistenceService", batches);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "talentIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(
        manager,
        "talentProcessEventPersistenceService",
        events);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
    ReflectionTestUtils.setField(manager, "jobContexts", jobs);
  }

  @Test
  void batchUploadRequiresOneJdVersionBeforeAnyWrite() {
    assertThatThrownBy(() -> manager
        .createBatch(new CreateIntakeBatchDTO("channel", null, null), "batch-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_JD_VERSION_REQUIRED"));
    verifyNoInteractions(jobs);
    verifyNoInteractions(batches, persistence);
  }

  @Test
  void visibleJdIsPersistedAndReturnedWithoutStartingWorkflow() {
    assertThat(
            manager
                .createBatch(
                    new CreateIntakeBatchDTO("channel", null, null, "jd-version"), "batch-key")
                .getJdVersionId())
        .isEqualTo("jd-version");
    verify(jobs).requireVersionForNewUse("jd-version");
  }

  @Test
  void inaccessibleJdIsRejectedBeforeBatchOrIdempotencyWrites() {
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权查看岗位"))
        .when(jobs).requireVersionForNewUse("private-jd");
    assertThatThrownBy(
            () ->
                manager.createBatch(
                    new CreateIntakeBatchDTO("channel", null, null, "private-jd"), "batch-key"))
        .hasMessageContaining("无权");
    verifyNoInteractions(batches, persistence);
  }

  @Test
  void completedBatchReplaySurvivesDisabledChannelAndInaccessibleJdWithoutWrites() {
    channel.setStatus("DISABLED");
    IntakeBatch original = historicalBatch("jd-version");
    original.setId("batch");
    original.setSourceChannelId("channel");
    original.setStatus(IntakeBatchStatus.COMPLETED.getCode());
    when(batches.findBatch("batch")).thenReturn(original);
    var previous = new IdempotencyRecord();
    previous.setActionCode("INTAKE_BATCH_CREATE");
    previous.setRequestFingerprint(Sha256Utils.hashUtf8(
        "channel\u001fhr\u001fdepartment\u001fjd-version"));
    previous.setTargetId("batch");
    when(idempotency.findIdempotency("hr", "batch-key")).thenReturn(previous);
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "岗位已不可见"))
        .when(jobs).requireVersionForNewUse("jd-version");

    assertThat(manager.createBatch(
        new CreateIntakeBatchDTO("channel", null, null, "jd-version"), "batch-key").getId())
        .isEqualTo("batch");

    verify(auth).requireAccess(eq("talent.manage"), any());
    verifyNoInteractions(jobs, persistence, events);
    verify(batches, never()).insertBatch(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), any());
  }

  @Test
  void disabledChannelStillRejectsNewBatch() {
    channel.setStatus("DISABLED");

    assertThatThrownBy(() -> manager.createBatch(
        new CreateIntakeBatchDTO("channel", null, null, "jd-version"), "new-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SOURCE_CHANNEL_DISABLED"));

    verifyNoInteractions(jobs, persistence, batches);
  }

  @Test
  void completedBatchRejectsSameKeyWithDifferentJd() {
    channel.setStatus("DISABLED");
    var previous = new IdempotencyRecord();
    previous.setActionCode("INTAKE_BATCH_CREATE");
    previous.setRequestFingerprint(Sha256Utils.hashUtf8(
        "channel\u001fhr\u001fdepartment\u001fjd-version"));
    previous.setTargetId("batch");
    when(idempotency.findIdempotency("hr", "batch-key")).thenReturn(previous);

    assertThatThrownBy(() -> manager.createBatch(
        new CreateIntakeBatchDTO("channel", null, null, "another-jd"), "batch-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

    verifyNoInteractions(jobs, persistence, batches);
  }

  @Test
  void linksVisibleJdToHistoricalBatchAndAuditsReason() {
    IntakeBatch before = historicalBatch(null);
    IntakeBatch after = historicalBatch(JD_VERSION_1);
    when(batches.findBatch("history-batch")).thenReturn(before, after);
    when(batches.linkJdVersion("history-batch", JD_VERSION_1, "hr", NOW)).thenReturn(1);

    assertThat(manager.linkBatchJdVersion(
            "history-batch", new LinkIntakeBatchJdDTO(JD_VERSION_1, "核对历史收件")))
        .isSameAs(after);

    verify(batches).linkJdVersion("history-batch", JD_VERSION_1, "hr", NOW);
    verify(jobs).requireVersionForNewUse(JD_VERSION_1);
    verify(events).insertEvent(
        "batch", "INTAKE_BATCH_JD_LINKED", "hr", "INTAKE_BATCH", "history-batch",
        "核对历史收件", NOW);
  }

  @Test
  void repeatedSameJdIsIdempotentButExistingBatchCannotBeRebound() {
    when(batches.findBatch("history-batch")).thenReturn(historicalBatch(JD_VERSION_1));

    assertThat(manager.linkBatchJdVersion(
            "history-batch", new LinkIntakeBatchJdDTO(JD_VERSION_1, "重复提交"))
        .getJdVersionId()).isEqualTo(JD_VERSION_1);
    assertThatThrownBy(() -> manager.linkBatchJdVersion(
            "history-batch", new LinkIntakeBatchJdDTO(JD_VERSION_2, "尝试改绑")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_BATCH_JD_ALREADY_LINKED"));

    verify(batches, never()).linkJdVersion(anyString(), anyString(), anyString(), any());
    verifyNoInteractions(jobs, events);
  }

  @Test
  void concurrentSameJdWriteIsTreatedAsIdempotent() {
    when(batches.findBatch("history-batch"))
        .thenReturn(historicalBatch(null), historicalBatch(JD_VERSION_1));
    when(batches.linkJdVersion("history-batch", JD_VERSION_1, "hr", NOW)).thenReturn(0);

    assertThat(manager.linkBatchJdVersion(
            "history-batch", new LinkIntakeBatchJdDTO(JD_VERSION_1, "并发核对"))
        .getJdVersionId()).isEqualTo(JD_VERSION_1);

    verifyNoInteractions(events);
  }

  @Test
  void concurrentDifferentJdWriteReturnsConflict() {
    when(batches.findBatch("history-batch"))
        .thenReturn(historicalBatch(null), historicalBatch(JD_VERSION_2));
    when(batches.linkJdVersion("history-batch", JD_VERSION_1, "hr", NOW)).thenReturn(0);

    assertThatThrownBy(() -> manager.linkBatchJdVersion(
            "history-batch", new LinkIntakeBatchJdDTO(JD_VERSION_1, "并发核对")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_BATCH_JD_LINK_CONFLICT"));

    verifyNoInteractions(events);
  }

  private static IntakeBatch historicalBatch(String jdVersionId) {
    IntakeBatch batch = new IntakeBatch();
    batch.setId("history-batch");
    batch.setSourceRecruiterUserId("hr");
    batch.setDefaultOwnerUserId("hr");
    batch.setOwnerDepartmentId("department");
    batch.setJdVersionId(jdVersionId);
    return batch;
  }
}
