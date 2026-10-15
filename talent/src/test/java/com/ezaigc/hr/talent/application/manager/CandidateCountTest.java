package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateCountTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final CursorCodec codec = mock(CursorCodec.class);
  private final TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();

  @BeforeEach
  void setUp() {
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(
        "owner", "HR", "hr@example.invalid", "home-department",
        Set.of("support-department"), Set.of("managed-owner"), Set.of("explicit-department"),
        Set.of("HR"), Set.of("talent.read"), false, false));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "candidateLibrary", library);
    ReflectionTestUtils.setField(manager, "cursorCodec", codec);
  }

  @Test
  void excludedBatchStillAdvancesAndLookaheadIsCountedOnlyInNextBatch() {
    var rows = IntStream.range(0, 201).mapToObj(CandidateCountTest::candidate).toList();
    var scanned = rows.subList(0, 200);
    var boundary = scanned.getLast();
    when(persistence.findCandidates("Java", "owner", false, Set.of(), Set.of(),
        true, null, null, 201)).thenReturn(rows);
    when(library.personalPoolCandidateIds(scanned.stream().map(Candidate::getId).toList(),
        null, "PENDING_DISPOSAL")).thenReturn(Set.of());
    when(codec.encode(any(), eq(List.of(boundary.getUpdateTime().toString(), boundary.getId()))))
        .thenReturn("next-count");
    when(codec.decode(eq("next-count"), any(), eq(2)))
        .thenReturn(List.of(boundary.getUpdateTime().toString(), boundary.getId()));
    when(persistence.findCandidates("Java", "owner", false, Set.of(), Set.of(),
        true, boundary.getUpdateTime(), boundary.getId(), 201)).thenReturn(List.of(rows.getLast()));
    when(library.personalPoolCandidateIds(List.of(rows.getLast().getId()), null, "PENDING_DISPOSAL"))
        .thenReturn(Set.of(rows.getLast().getId()));

    var first = manager.countCandidates("Java", null, true, "PENDING_DISPOSAL");
    assertThat(first.getCount()).isZero();
    assertThat(first.isHasMore()).isTrue();
    assertThat(first.getNextCursor()).isEqualTo("next-count");
    verify(persistence).findCandidates("Java", "owner", false, Set.of(), Set.of(),
        true, null, null, 201);
    verifyNoMoreInteractions(persistence);

    var last = manager.countCandidates("Java", first.getNextCursor(), true, "PENDING_DISPOSAL");
    assertThat(last.getCount()).isEqualTo(1);
    assertThat(last.isHasMore()).isFalse();
    assertThat(last.getNextCursor()).isNull();
  }

  @Test
  void countUsesSameRealtimePersonalPoolEligibilityAsList() {
    var rows = List.of(candidate(1), candidate(2), candidate(3));
    when(persistence.findCandidates(null, "owner", false, Set.of(), Set.of(),
        true, null, null, 201)).thenReturn(rows);
    when(library.personalPoolCandidateIds(rows.stream().map(Candidate::getId).toList()))
        .thenReturn(Set.of(rows.get(1).getId()));

    var result = manager.countCandidates(null, null, true, null);

    assertThat(result.getCount()).isEqualTo(1);
    assertThat(result.isHasMore()).isFalse();
    verify(authorization).requirePermission("talent.read");
  }

  @Test
  void nonPoolDirectoryCountsEveryVisibleRowWithoutPersonalPoolFiltering() {
    when(persistence.findCandidates(null, "owner", false, Set.of(), Set.of(),
        false, null, null, 201)).thenReturn(List.of(candidate(1), candidate(2)));

    var result = manager.countCandidates(null, null, false, null);

    assertThat(result.getCount()).isEqualTo(2);
    assertThat(result.isHasMore()).isFalse();
    verifyNoInteractions(library);
  }

  @Test
  void emptyScopeReturnsZeroAndRejectsStageWithoutPoolMode() {
    when(persistence.findCandidates(null, "owner", false, Set.of(), Set.of(),
        true, null, null, 201)).thenReturn(List.of());
    assertThat(manager.countCandidates(null, null, true, null).getCount()).isZero();
    verifyNoInteractions(library);
    assertThatThrownBy(() -> manager.countCandidates(null, null, false, "PENDING_SCREENING"))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void listOrDifferentFilterCursorCannotBeReusedForCounting() {
    when(codec.decode(eq("list-cursor"), any(), eq(2)))
        .thenThrow(new IllegalArgumentException("cursor scope mismatch"));

    assertThatThrownBy(() -> manager.countCandidates("Java", "list-cursor", true, "PENDING_SCREENING"))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(persistence, library);
  }

  private static Candidate candidate(int index) {
    var result = new Candidate();
    result.setId(String.format("%026d", index));
    result.setUpdateTime(Instant.parse("2026-09-20T00:00:00Z").minusSeconds(index));
    return result;
  }
}
