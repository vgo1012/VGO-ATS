package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.application.assembler.RiskAssembler;
import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.service.CompanyBlacklistCurrentPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class RiskHistoryTest {
  private final RiskApplicationManager manager = new RiskApplicationManager();
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
  private final RiskPersistenceService persistence = mock(RiskPersistenceService.class);
  private final CandidateDirectoryService candidates = mock(CandidateDirectoryService.class);
  private final CompanyBlacklistCurrentPersistenceService current = mock(CompanyBlacklistCurrentPersistenceService.class);

  @BeforeEach void setup() {
    ReflectionTestUtils.setField(manager, "library", library);
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.getUserId()).thenReturn("hr");
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    CandidatePersistenceService candidatePersistence = mock(CandidatePersistenceService.class);
    when(candidatePersistence.lockCandidate("candidate")).thenReturn("candidate");
    ReflectionTestUtils.setField(manager, "candidatePersistence", candidatePersistence);
    ReflectionTestUtils.setField(manager, "companyBlacklistCurrentPersistenceService", current);
    ReflectionTestUtils.setField(manager, "assembler", new RiskAssembler());
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("risk-history-disposable-test-signing-key"));
  }

  @Test void requiresReadPermissionBeforeQueryingHistoryOrAffectedResources() {
    doThrow(PlatformExceptions.forbidden("DENIED", "无风险读取权限"))
        .when(authorization).requirePermission("risk.read");
    assertThatThrownBy(() -> manager.blacklistHistory("candidate", null, 1)).hasMessageContaining("权限");
    when(persistence.findBlacklist("record")).thenReturn(record("Z", "ACTIVE"));
    assertThatThrownBy(() -> manager.affectedPage("record", null, 1)).hasMessageContaining("权限");
    verify(persistence, never()).blacklistHistory(anyString(), any(), any(), anyInt());
    verify(persistence, never()).affectedPage(anyString(), any(), any(), anyInt());
    verifyNoInteractions(candidates);
  }

  @Test void sharedRiskHistoryAndAffectedRecordsAreReadableWithoutRiskRoleAndRevokeOnExit() {
    var denied = PlatformExceptions.forbidden("DENIED", "无风险读取权限");
    doThrow(denied).when(authorization).requirePermission("risk.read");
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of("candidate"));
    when(persistence.findBlacklist("record")).thenReturn(record("Z", "ACTIVE"));
    assertThat(manager.blacklistHistory("candidate", null, 20).getRecords().getItems()).isEmpty();
    assertThat(manager.affectedPage("record", null, 20).getItems()).isEmpty();
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of());
    assertThatThrownBy(() -> manager.blacklistHistory("candidate", null, 20)).isSameAs(denied);
    assertThatThrownBy(() -> manager.affectedPage("record", null, 20)).isSameAs(denied);
  }

  @Test void historyKeepsCurrentProjectionAndBindsPaginationToCandidateAndActor() {
    var latest = record("Z", "DISABLED");
    when(persistence.currentBlacklist("candidate")).thenReturn(latest);
    when(persistence.blacklistHistory(anyString(), any(), any(), anyInt()))
        .thenReturn(List.of(record("B", "CORRECTED"), record("A", "ACTIVE")));
    var history = manager.blacklistHistory("candidate", null, 1);
    assertThat(history.getCurrent().getId()).isEqualTo(latest.getId());
    assertThat(history.isActive()).isFalse();
    assertThat(history.getRecords().getItems()).hasSize(1);
    assertThat(history.getRecords().isHasMore()).isTrue();
    var cursor = history.getRecords().getNextCursor();
    manager.blacklistHistory("candidate", cursor, 1);
    verify(persistence).blacklistHistory("candidate", Instant.EPOCH, "B".repeat(26), 2);
    assertThatThrownBy(() -> manager.blacklistHistory("other", cursor, 1)).hasMessageContaining("游标");
    when(actor.getUserId()).thenReturn("another-hr");
    assertThatThrownBy(() -> manager.blacklistHistory("candidate", cursor, 1)).hasMessageContaining("游标");
  }

  @Test void affectedResourcesUseNextPageAndRejectAnotherRecordCursor() {
    when(persistence.findBlacklist(anyString())).thenReturn(record("Z", "ACTIVE"));
    when(persistence.affectedPage(anyString(), any(), any(), anyInt())).thenReturn(List.of(
        new AffectedWorkflow("APPLICATION", "B".repeat(26), "app", null, "jd", "OFFER", "hr", Instant.EPOCH),
        new AffectedWorkflow("CLIENT_SUBMISSION", "A".repeat(26), "app", "client", "jd", "SENT/SCREENING", "hr", Instant.EPOCH)));
    var page = manager.affectedPage("record", null, 1);
    assertThat(page.getItems()).hasSize(1);
    assertThat(page.isHasMore()).isTrue();
    manager.affectedPage("record", page.getNextCursor(), 1);
    verify(persistence).affectedPage("candidate", Instant.EPOCH, "B".repeat(26), 2);
    assertThatThrownBy(() -> manager.affectedPage("other", page.getNextCursor(), 1)).hasMessageContaining("游标");
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void disabledRecordCannotBeCorrectedIntoAnActiveProjectionOrDisabledAgain(boolean correction) {
    var disabled = record("Z", "DISABLED");
    when(persistence.findBlacklist(disabled.getId())).thenReturn(disabled);
    when(persistence.currentBlacklist("candidate")).thenReturn(disabled);
    CompanyBlacklistCurrent projection = new CompanyBlacklistCurrent();
    projection.setCandidateId("candidate"); projection.setRecordId(disabled.getId()); projection.setActive(false);
    when(current.lockCurrent("candidate")).thenReturn(projection);
    assertThatThrownBy(() -> {
      if (correction) manager.correctBlacklist(disabled.getId(), "QA", "合成依据", "MANUAL", "test", Instant.EPOCH);
      else manager.disableBlacklist(disabled.getId(), "合成依据");
    }).hasMessageContaining("当前生效记录");
    verify(current).lockCurrent("candidate");
    verify(current, never()).projectBlacklist(any(), any(), any(), anyBoolean(), any(), any());
  }

  private CompanyBlacklistRecord record(String id, String status) {
    var record = new CompanyBlacklistRecord();
    record.setId(id.repeat(26)); record.setCandidateId("candidate"); record.setStatus(status);
    record.setRecordType("ACTIVATION"); record.setCreateTime(Instant.EPOCH); record.setOccurredAt(Instant.EPOCH);
    return record;
  }
}
