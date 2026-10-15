package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.EngagementLockAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.CollaborationRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockHistory;
import com.ezaigc.hr.recruiting.recruitment.enums.CollaborationRequestStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.EngagementLockStatus;
import com.ezaigc.hr.recruiting.recruitment.service.CollaborationRequestPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EngagementLockApplicationManagerBatchQueryTest {

  private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
  private static final String ACTOR_ID = "U".repeat(26);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CandidateLibraryQueryService candidateLibrary = mock(CandidateLibraryQueryService.class);
  private final EngagementLockPersistenceService locks =
      mock(EngagementLockPersistenceService.class);
  private final CollaborationRequestPersistenceService collaborations =
      mock(CollaborationRequestPersistenceService.class);
  private final LockHistoryPersistenceService histories =
      mock(LockHistoryPersistenceService.class);
  private final EngagementLockApplicationManager manager = new EngagementLockApplicationManager();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", candidateLibrary);
    ReflectionTestUtils.setField(manager, "assembler", new EngagementLockAssembler());
    ReflectionTestUtils.setField(manager, "persistence", locks);
    ReflectionTestUtils.setField(
        manager, "collaborationRequestPersistenceService", collaborations);
    ReflectionTestUtils.setField(manager, "lockHistoryPersistenceService", histories);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    when(authorization.currentActor())
        .thenReturn(
            new CurrentActorSnapshot(
                ACTOR_ID,
                "招聘顾问",
                "recruiter@example.com",
                "D".repeat(26),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("RECRUITER"),
                Set.of("talent.read"),
                false,
                false));
  }

  @Test
  void loadsLocksAndActorCollaborationsInTwoBatchQueries() {
    EngagementLock owned = lock("L".repeat(26), "A".repeat(26), ACTOR_ID);
    EngagementLock collaborationLock =
        lock("M".repeat(26), "B".repeat(26), "O".repeat(26));
    CollaborationRequest accepted = new CollaborationRequest();
    accepted.setLockId(collaborationLock.getId());
    accepted.setStatus(CollaborationRequestStatus.ACCEPTED.getCode());
    when(locks.activeByCandidates(
            List.of(owned.getCandidateId(), collaborationLock.getCandidateId()), NOW))
        .thenReturn(List.of(owned, collaborationLock));
    when(collaborations.activeCollaborations(
            List.of(owned.getId(), collaborationLock.getId()), ACTOR_ID))
        .thenReturn(List.of(accepted));

    var result =
        manager.activeLocks(List.of(owned.getCandidateId(), collaborationLock.getCandidateId()));

    assertThat(result).containsOnlyKeys(owned.getCandidateId(), collaborationLock.getCandidateId());
    assertThat(result.get(owned.getCandidateId()).isCanContact()).isTrue();
    assertThat(result.get(collaborationLock.getCandidateId()).isCanContact()).isTrue();
    assertThat(result.get(collaborationLock.getCandidateId()).getCollaborationStatus())
        .isEqualTo(CollaborationRequestStatus.ACCEPTED.getCode());
    verify(locks)
        .activeByCandidates(
            List.of(owned.getCandidateId(), collaborationLock.getCandidateId()), NOW);
    verify(collaborations)
        .activeCollaborations(List.of(owned.getId(), collaborationLock.getId()), ACTOR_ID);
  }

  @Test
  void skipsPersistenceForAnEmptyCandidateBatch() {
    assertThat(manager.activeLocks(List.of())).isEmpty();

    verifyNoInteractions(locks, collaborations);
  }

  @Test
  void nonSharedCandidateReadDoesNotRevealAnotherRecruitersLockHistory() {
    String candidateId = "C".repeat(26);
    EngagementLock otherRecruitersLock =
        lock("L".repeat(26), candidateId, "O".repeat(26));
    when(locks.findByCandidate(candidateId)).thenReturn(otherRecruitersLock);
    doThrow(PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权查看他人跟进历史"))
        .when(authorization)
        .requireAccess(any(String.class), any(AuthorizationTarget.class));

    assertThatThrownBy(() -> manager.history(candidateId, 50))
        .hasMessageContaining("无权查看他人跟进历史");

    verify(histories, never()).history(any(String.class), anyInt());
  }

  @Test
  void sharedCandidateHistoryIncludesOtherRecruitersRecordsAndRevokesAccessAfterLeaving() {
    String candidateId = "C".repeat(26);
    EngagementLock previousLock = lock("L".repeat(26), candidateId, "O".repeat(26));
    previousLock.setStatus(EngagementLockStatus.RELEASED.getCode());
    when(locks.findByCandidate(candidateId)).thenReturn(previousLock);
    when(candidateLibrary.eligibleCandidateIds(List.of(candidateId))).thenReturn(Set.of(candidateId));
    var history = new LockHistory();
    history.setId("H".repeat(26));
    history.setCandidateId(candidateId);
    history.setReason("前负责人释放跟进归属");
    when(histories.history(candidateId, 50)).thenReturn(List.of(history));
    doThrow(PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权查看他人跟进历史"))
        .when(authorization).requireAccess(any(String.class), any(AuthorizationTarget.class));

    assertThat(manager.history(candidateId, 50)).singleElement()
        .satisfies(record -> assertThat(record.getReason()).isEqualTo("前负责人释放跟进归属"));
    verify(authorization, never()).requireAccess(any(String.class), any(AuthorizationTarget.class));

    when(candidateLibrary.eligibleCandidateIds(List.of(candidateId))).thenReturn(Set.of());
    assertThatThrownBy(() -> manager.history(candidateId, 50))
        .hasMessageContaining("无权查看他人跟进历史");
    verify(histories).history(candidateId, 50);
  }

  private static EngagementLock lock(String lockId, String candidateId, String ownerId) {
    EngagementLock value = new EngagementLock();
    value.setId(lockId);
    value.setCandidateId(candidateId);
    value.setOwnerUserId(ownerId);
    value.setOwnerDepartmentId("D".repeat(26));
    value.setContactName("招聘顾问");
    value.setStatus(EngagementLockStatus.ACTIVE.getCode());
    value.setAcquiredAt(NOW.minusSeconds(60));
    value.setExpiresAt(NOW.plusSeconds(3600));
    return value;
  }
}
