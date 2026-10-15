package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.mapper.IdentityConflictMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.ibatis.annotations.Options;

class IdentityConflictRecorderTest {
  private final IdentityConflictPersistenceService persistence = mock(IdentityConflictPersistenceService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final IdentityConflictRecorder recorder = new IdentityConflictRecorder();
  private final Instant now = Instant.parse("2026-10-09T08:00:00Z");

  IdentityConflictRecorderTest() {
    ReflectionTestUtils.setField(recorder, "identityConflictPersistenceService", persistence);
    ReflectionTestUtils.setField(recorder, "ids", ids);
    ReflectionTestUtils.setField(recorder, "clock", Clock.fixed(now, ZoneOffset.UTC));
  }

  @ParameterizedTest
  @ValueSource(strings = {"READY_FOR_CONFIRMATION", "IDENTITY_REVIEW_REQUIRED"})
  void locksCurrentIntakeBeforeCheckingAndInserting(String status) {
    when(persistence.lockIntake("intake")).thenReturn(intake(status, 4));
    when(ids.newId()).thenReturn("conflict");

    assertThat(recorder.record("intake", "candidate", null, "CONTACT_NAME_MISMATCH", 4))
        .isEqualTo("conflict");

    var order = inOrder(persistence);
    order.verify(persistence).lockCandidateForConflict("candidate");
    order.verify(persistence).lockIntake("intake");
    order.verify(persistence).findOpenConflictForIntake("intake");
    order.verify(persistence).insertConflict("conflict", "intake", "candidate", null,
        "CONTACT_NAME_MISMATCH", now);
  }

  @ParameterizedTest
  @ValueSource(strings = {"READY_FOR_CONFIRMATION", "IDENTITY_REVIEW_REQUIRED"})
  void reusesOpenConflictOnlyAfterLockingAndCheckingTheSameIntakeVersion(String status) {
    when(persistence.lockIntake("intake")).thenReturn(intake(status, 4));
    var existing = new IdentityConflict();
    existing.setId("existing");
    when(persistence.findOpenConflictForIntake("intake")).thenReturn(existing);

    assertThat(recorder.record("intake", "candidate", null, "CONTACT_NAME_MISMATCH", 4))
        .isEqualTo("existing");

    var order = inOrder(persistence);
    order.verify(persistence).lockCandidateForConflict("candidate");
    order.verify(persistence).lockIntake("intake");
    order.verify(persistence).findOpenConflictForIntake("intake");
    verify(persistence, never()).insertConflict(anyString(), anyString(), anyString(), any(), anyString(), any());
    verify(ids, never()).newId();
  }

  @ParameterizedTest
  @CsvSource({
      "CONFIRMED, 4", "COMPLETED, 4", "PARSING, 4", "READY_FOR_CONFIRMATION, 5",
      "IDENTITY_REVIEW_REQUIRED, 5", "MISSING, 4"
  })
  void rejectsStaleOrUnavailableJudgmentWithoutCreatingAnOpenConflict(String status, long version) {
    when(persistence.lockIntake("intake"))
        .thenReturn("MISSING".equals(status) ? null : intake(status, version));

    assertThatThrownBy(() -> recorder.record("intake", "candidate", null, "CONTACT_NAME_MISMATCH", 4))
        .isInstanceOfSatisfying(PlatformException.class,
            failure -> assertThat(failure.code()).isEqualTo("VERSION_CONFLICT"));

    verify(persistence, never()).findOpenConflictForIntake(anyString());
    verify(persistence, never()).insertConflict(anyString(), anyString(), anyString(), any(), anyString(), any());
    verify(ids, never()).newId();
  }

  @Test
  void locksBothCandidateParentsInStableOrderBeforeLockingTheIntake() {
    when(persistence.lockIntake("intake")).thenReturn(intake("READY_FOR_CONFIRMATION", 4));
    when(ids.newId()).thenReturn("conflict");

    recorder.record("intake", "z-candidate", "a-candidate", "STRONG_IDENTITY_MISMATCH", 4);

    var order = inOrder(persistence);
    order.verify(persistence).lockCandidateForConflict("a-candidate");
    order.verify(persistence).lockCandidateForConflict("z-candidate");
    order.verify(persistence).lockIntake("intake");
  }

  @Test
  void locksTheSameCandidateParentOnlyOnce() {
    when(persistence.lockIntake("intake")).thenReturn(intake("READY_FOR_CONFIRMATION", 4));
    when(ids.newId()).thenReturn("conflict");

    recorder.record("intake", "candidate", "candidate", "STRONG_IDENTITY_MISMATCH", 4);

    verify(persistence).lockCandidateForConflict("candidate");
  }

  @ParameterizedTest
  @ValueSource(strings = {"lockIntake", "lockCandidateForConflict"})
  void lockQueriesClearSessionCacheBeforeProtectedRereads(String method) throws Exception {
    var options = IdentityConflictMapper.class.getMethod(method, String.class).getAnnotation(Options.class);
    assertThat(options).isNotNull();
    assertThat(options.flushCache()).isEqualTo(Options.FlushCachePolicy.TRUE);
    assertThat(options.useCache()).isFalse();
  }

  private static ResumeIntake intake(String status, long version) {
    var intake = new ResumeIntake();
    intake.setId("intake");
    intake.setStatus(status);
    intake.setVersion(version);
    return intake;
  }
}
