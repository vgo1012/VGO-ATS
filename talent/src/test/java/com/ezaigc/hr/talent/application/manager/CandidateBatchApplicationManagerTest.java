package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateBatchApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateReadAccess access = mock(CandidateReadAccess.class);
  private final CandidateBatchApplicationManager manager = new CandidateBatchApplicationManager();

  CandidateBatchApplicationManagerTest() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "access", access);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
  }

  @Test
  void batchesOnceAndPreservesRequestedOrderWithoutExposingUnreadableOrDeletedPeople() {
    var first = candidate("01J00000000000000000000001", "ACTIVE");
    var second = candidate("01J00000000000000000000002", "ACTIVE");
    var hidden = candidate("01J00000000000000000000003", "ACTIVE");
    var deleted = candidate("01J00000000000000000000004", "DELETED");
    when(candidates.listByIds(List.of("01J00000000000000000000002", "01J00000000000000000000001", "01J00000000000000000000003", "01J00000000000000000000004", "01J00000000000000000000005")))
        .thenReturn(List.of(first, hidden, deleted, second));
    when(access.evaluate(List.of(first, hidden, second)))
        .thenReturn(new CandidateReadDecision(Set.of("01J00000000000000000000001", "01J00000000000000000000002"), Set.of(), Map.of()));

    var result = manager.read(List.of("01J00000000000000000000002", "01J00000000000000000000001", "01J00000000000000000000002", "01J00000000000000000000003", "01J00000000000000000000004", "01J00000000000000000000005"));

    assertThat(result).extracting("id").containsExactly("01J00000000000000000000002", "01J00000000000000000000001");
    assertThat(result.getFirst().getPrimaryPhone()).isEqualTo("synthetic-phone");
    verify(candidates).listByIds(List.of("01J00000000000000000000002", "01J00000000000000000000001", "01J00000000000000000000003", "01J00000000000000000000004", "01J00000000000000000000005"));
    verify(access).evaluate(List.of(first, hidden, second));
    verify(authorization).currentActor();
  }

  @Test
  void emptyRequestStillRequiresALoginAndDoesNotQueryDatabase() {
    assertThat(manager.read(List.of())).isEmpty();
    verify(authorization).currentActor();
    verifyNoInteractions(candidates, access);
  }

  @Test
  void rejectsOversizedAndMalformedInputBeforeReadingCandidates() {
    assertThatThrownBy(() -> manager.read(Collections.nCopies(101, "candidate")))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.read(List.of(" "))).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.read(null)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(candidates, access);
  }

  private static Candidate candidate(String id, String status) {
    var row = new Candidate();
    row.setId(id);
    row.setStatus(status);
    row.setFullName("合成候选人");
    row.setPrimaryPhone("synthetic-phone");
    row.setVersion(1L);
    return row;
  }
}
