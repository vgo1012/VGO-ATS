package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.application.adapter.CandidateDirectoryAdapter;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.CompanyBlacklistCurrentPersistenceService;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class CompanyBlacklistCommandPolicyTest {
  private final RiskApplicationManager manager = new RiskApplicationManager();
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateDirectoryService directory = new CandidateDirectoryAdapter();
  private final CompanyBlacklistCurrentPersistenceService current = mock(CompanyBlacklistCurrentPersistenceService.class);

  CompanyBlacklistCommandPolicyTest() {
    ReflectionTestUtils.setField(manager, "candidatePersistence", candidates);
    ReflectionTestUtils.setField(directory, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "candidates", directory);
    ReflectionTestUtils.setField(manager, "companyBlacklistCurrentPersistenceService", current);
    var candidate = new Candidate(); candidate.setId("candidate"); candidate.setStatus("ACTIVE");
    when(candidates.findCandidateForUpdate("candidate")).thenReturn(candidate);
  }

  @ParameterizedTest @ValueSource(booleans={true,false})
  void newCommandsUseCurrentProjectionRatherThanAnOlderSnapshot(boolean active) {
    var projection = new CompanyBlacklistCurrent(); projection.setCandidateId("candidate");
    projection.setRecordId("record"); projection.setActive(active);
    when(current.lockCurrent("candidate")).thenReturn(projection);
    // 旧 UI 快照看不到风险，不得用于写命令裁决。
    when(current.isBlacklisted("candidate")).thenReturn(0);
    if (active) {
      assertThatThrownBy(() -> manager.requireRecruitmentAllowed("candidate"))
          .isInstanceOfSatisfying(PlatformException.class, error -> {
            assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED");
            assertThat(error.status().value()).isEqualTo(409);
          });
    } else manager.requireRecruitmentAllowed("candidate");
    var order = inOrder(candidates, current);
    order.verify(candidates).findCandidateForUpdate("candidate");
    order.verify(current).lockCurrent("candidate");
    verify(current, never()).isBlacklisted("candidate");
  }

  @Test void absentProjectionAllowsNewCommandsAndLockDoesNotApplyPolicy() {
    when(candidates.lockCandidate("candidate")).thenReturn("candidate");
    manager.lockCandidateForCommand("candidate");
    verify(current, never()).lockCurrent("candidate");
    verify(candidates, never()).findCandidateForUpdate("candidate");
    manager.requireRecruitmentAllowed("candidate");
    verify(current).lockCurrent("candidate");
  }

  @Test void inactiveCandidateRejectsNewFactsBeforeBlacklistProjectionRead() {
    var merged = new Candidate(); merged.setId("candidate"); merged.setStatus("DELETED");
    when(candidates.findCandidateForUpdate("candidate")).thenReturn(merged);
    // 历史回放的纯 C 锁仍允许停用候选；正向命令必须用精确当前行判断。
    when(candidates.lockCandidate("candidate")).thenReturn("candidate");
    manager.lockCandidateForCommand("candidate");
    verify(candidates, never()).findCandidateForUpdate("candidate");
    assertThatThrownBy(() -> manager.requireRecruitmentAllowed("candidate"))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("CANDIDATE_NOT_FOUND");
          assertThat(error.status().value()).isEqualTo(404);
        });
    verify(current, never()).lockCurrent("candidate");
    verify(current, never()).isBlacklisted("candidate");
    verify(candidates, never()).findCandidate("candidate");
  }

  @Test void missingCandidateFailsBeforeRecruitmentFactsCanBeLocked() {
    assertThatThrownBy(() -> manager.lockCandidateForCommand("missing"))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("CANDIDATE_NOT_FOUND");
          assertThat(error.status().value()).isEqualTo(404);
        });
  }

  @Test void candidateLockAndCurrentReadJoinCallerTransactionAndWritersAvoidMissingRowGapLocks() {
    var methods = Arrays.stream(RiskApplicationManager.class.getDeclaredMethods()).toList();
    for (String name : Set.of("lockCandidateForCommand", "requireRecruitmentAllowed")) {
      var transaction = methods.stream().filter(method -> method.getName().equals(name)).findFirst().orElseThrow()
          .getAnnotation(Transactional.class);
      assertThat(transaction.propagation()).isEqualTo(Propagation.MANDATORY);
      assertThat(transaction.readOnly()).isFalse();
    }
    for (String name : Set.of("createBlacklist", "correctBlacklist", "disableBlacklist")) {
      var transaction = methods.stream().filter(method -> method.getName().equals(name)).findFirst().orElseThrow()
          .getAnnotation(Transactional.class);
      assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
      assertThat(transaction.readOnly()).isFalse();
    }
  }
}
