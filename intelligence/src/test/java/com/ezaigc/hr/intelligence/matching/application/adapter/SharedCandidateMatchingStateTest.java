package com.ezaigc.hr.intelligence.matching.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.entity.CandidateMatchingStateRow;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SharedCandidateMatchingStateTest {
  @Test
  void sharedHistoryIsReadableWithoutMatchingPermissionAndNeverIncludesPrivateCandidates() {
    CandidateMatchingStateAdapter adapter = new CandidateMatchingStateAdapter();
    AuthorizationService authorization = mock(AuthorizationService.class);
    CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
    WeightedResultPersistenceService results = mock(WeightedResultPersistenceService.class);
    ReflectionTestUtils.setField(adapter, "authorization", authorization);
    ReflectionTestUtils.setField(adapter, "admissions", mock(ScreeningAdmissionPersistenceService.class));
    ReflectionTestUtils.setField(adapter, "candidateLibrary", library);
    ReflectionTestUtils.setField(adapter, "results", results);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(
        "reader", "Reader", null, "other", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false));
    when(library.eligibleCandidateIds(List.of("shared", "private"))).thenReturn(Set.of("shared"));
    CandidateMatchingStateRow row = new CandidateMatchingStateRow();
    row.setCandidateId("shared");
    row.setResumeVersionId("resume");
    row.setJdVersionId("jd");
    row.setMatchRunId("run");
    when(results.findCandidateMatchingStates(List.of("shared"), null)).thenReturn(List.of(row));

    assertThat(adapter.completedStates(List.of("shared", "private")))
        .extracting(value -> value.getCandidateId()).containsExactly("shared");
    when(library.eligibleCandidateIds(List.of("shared", "private"))).thenReturn(Set.of());
    assertThat(adapter.completedStates(List.of("shared", "private"))).isEmpty();
  }
}
