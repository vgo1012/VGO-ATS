package com.ezaigc.hr.talent.application.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRun;
import com.ezaigc.hr.talent.service.ResumeLifecycleRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeLifecycleAdministrationManagerTest {
  private final ResumeLifecycleAdministrationManager manager=new ResumeLifecycleAdministrationManager();
  private final AuthorizationService auth=mock(AuthorizationService.class);
  private final ResumeLifecycleRunPersistenceService runs=mock(ResumeLifecycleRunPersistenceService.class);
  private final ResumeLifecycleRuntimePersistenceService runtime=mock(ResumeLifecycleRuntimePersistenceService.class);
  private final TalentIdempotencyPersistenceService idempotency=mock(TalentIdempotencyPersistenceService.class);
  private final TalentIntakePersistenceService intakes=mock(TalentIntakePersistenceService.class);
  private final TalentProcessEventPersistenceService audit=mock(TalentProcessEventPersistenceService.class);
  private final ResumeLifecycleProperties properties=new ResumeLifecycleProperties();
  private final CurrentActorSnapshot actor=new CurrentActorSnapshot();
  private final ResumeLifecycleRun run=new ResumeLifecycleRun();

  @BeforeEach void setup() {
    actor.setUserId("admin");actor.setSuperAdmin(true);properties.setEnabled(true);
    when(auth.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager,"authorization",auth);
    ReflectionTestUtils.setField(manager,"runs",runs);
    ReflectionTestUtils.setField(manager,"runtime",runtime);
    ReflectionTestUtils.setField(manager,"idempotency",idempotency);
    ReflectionTestUtils.setField(manager,"intakes",intakes);
    ReflectionTestUtils.setField(manager,"audit",audit);
    ReflectionTestUtils.setField(manager,"properties",properties);
    ReflectionTestUtils.setField(manager,"ids",(PlatformIdGenerator)()->"id");
    ReflectionTestUtils.setField(manager,"clock",Clock.fixed(Instant.parse("2026-09-18T17:30:00Z"),ZoneOffset.UTC));
    run.setId("run");run.setStatus("PARTIAL");
    when(runs.updateById(any(ResumeLifecycleRun.class))).thenReturn(true);
    when(runs.getById("run")).thenReturn(run);
    when(runs.list(any(Wrapper.class))).thenReturn(List.of(run));
    when(intakes.insertIdempotency(anyString(),anyString(),anyString(),anyString(),anyString(),any())).thenReturn(1);
  }

  @Test void ordinaryUserCannotReadRunsOrRequestRetry() {
    actor.setSuperAdmin(false);
    assertThrows(RuntimeException.class,manager::status);
    assertThrows(RuntimeException.class,()->manager.retry("run","恢复重试","key"));
    verifyNoInteractions(runs,runtime,intakes,idempotency,audit);
  }

  @Test void disabledFeatureRejectsNewRetries() {
    properties.setEnabled(false);
    assertThrows(RuntimeException.class,()->manager.retry("run","恢复重试","key"));
    verify(runtime,never()).calendarChanged();
    verifyNoInteractions(intakes,audit);
  }

  @Test void successfulAndRunningRunsCannotBeRetried() {
    run.setStatus("SUCCEEDED");
    assertThrows(RuntimeException.class,()->manager.retry("run","恢复重试","key"));
    run.setStatus("RUNNING");
    assertThrows(RuntimeException.class,()->manager.retry("run","恢复重试","key"));
    verify(runtime,never()).calendarChanged();
    verifyNoInteractions(intakes,audit);
  }

  @Test void validRetrySchedulesOnceAndRecordsActualAdministrator() {
    run.setSettlementDate(LocalDate.of(2026,9,16));run.setCalendarGeneration(2);
    manager.retry("run","恢复重试","key");
    assertEquals("RETRY_PENDING",run.getStatus());
    assertEquals(LocalDate.of(2026,9,16),run.getSettlementDate());
    assertEquals(2,run.getCalendarGeneration());
    verify(runs).updateById(run);
    verify(runtime,never()).calendarChanged();
    verify(audit).insertEvent(eq("id"),eq("RESUME_LIFECYCLE_RETRY"),eq("admin"),
        eq("RESUME_LIFECYCLE_RUN"),eq("run"),eq("恢复重试"),any());
  }

  @Test void idempotentReplayDoesNotScheduleOrAuditAgainEvenAfterFeatureDisabled() {
    IdempotencyRecord replay=replay("恢复重试");
    when(idempotency.findIdempotency("admin","key")).thenReturn(replay);
    properties.setEnabled(false);run.setStatus("SUCCEEDED");
    assertDoesNotThrow(()->manager.retry("run","恢复重试","key"));
    verify(runtime,never()).calendarChanged();
    verifyNoInteractions(intakes,audit);
  }

  @Test void reusedKeyWithDifferentReasonIsRejected() {
    when(idempotency.findIdempotency("admin","key")).thenReturn(replay("旧原因"));
    assertThrows(RuntimeException.class,()->manager.retry("run","恢复重试","key"));
    verify(runtime,never()).calendarChanged();
    verifyNoInteractions(intakes,audit);
  }

  @Test void concurrentReservationLossCannotEnqueueDuplicateRetry() {
    when(intakes.insertIdempotency(anyString(),anyString(),anyString(),anyString(),anyString(),any())).thenReturn(0);
    assertThrows(RuntimeException.class,()->manager.retry("run","恢复重试","key"));
    verify(runtime,never()).calendarChanged();
    verifyNoInteractions(audit);
  }

  private static IdempotencyRecord replay(String reason) {
    IdempotencyRecord record=new IdempotencyRecord();record.setTargetId("run");
    record.setActionCode("RESUME_LIFECYCLE_RETRY");
    record.setRequestFingerprint(Sha256Utils.hashUtf8("run:"+reason));return record;
  }
}
