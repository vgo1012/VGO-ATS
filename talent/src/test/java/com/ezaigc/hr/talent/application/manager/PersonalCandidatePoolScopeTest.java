package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PersonalCandidatePoolScopeTest {

  @Test
  void candidateDirectoryKeepsManagementChainOutOfTheOperationalList() {
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(TalentIntakePersistenceService.class);
    var actor =
        new CurrentActorSnapshot(
            "test-manager",
            "测试负责人",
            "wang-wei@example.invalid",
            "home-department",
            Set.of("support-department"),
            Set.of("managed-recruiter"),
            Set.of("explicit-department"),
            Set.of("RECRUITMENT_MANAGER"),
            Set.of("talent.read"),
            true,
            false);
    when(authorization.currentActor()).thenReturn(actor);
    when(persistence.findCandidates(
            eq("Java"),
            eq("test-manager"),
            eq(true),
            eq(Set.of()),
            eq(Set.of()),
            eq(false),
            isNull(),
            isNull(),
            eq(21)))
        .thenReturn(List.of());

    var manager = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));

    manager.candidates("Java", null, 20);

    verify(authorization).requirePermission("talent.read");
    verify(persistence)
        .findCandidates(
            "Java", "test-manager", true, Set.of(), Set.of(), false, null, null, 21);
  }

  @Test
  void poolOnlyKeepsTheActorsOwnerScopeAndScansPastExcludedCandidates() {
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(TalentIntakePersistenceService.class);
    var library = mock(CandidateLibraryQueryService.class);
    var codec = mock(CursorCodec.class);
    var actor = actor();
    when(authorization.currentActor()).thenReturn(actor);
    var firstScan = new ArrayList<Candidate>();
    for (int index = 0; index < 101; index++) {
      firstScan.add(candidate(index, Instant.parse("2026-09-08T10:00:00Z").minusSeconds(index)));
    }
    var secondScan = List.of(
        candidate(101, Instant.parse("2026-09-08T09:00:00Z")),
        candidate(102, Instant.parse("2026-09-08T08:00:00Z")),
        candidate(103, Instant.parse("2026-09-08T07:00:00Z")));
    when(persistence.findCandidates(
            null, actor.getUserId(), true, Set.of(), Set.of(), true, null, null, 101))
        .thenReturn(firstScan);
    var scannedLast = firstScan.getLast();
    when(persistence.findCandidates(
            null,
            actor.getUserId(),
            true,
            Set.of(),
            Set.of(),
            true,
            scannedLast.getUpdateTime(),
            scannedLast.getId(),
            101))
        .thenReturn(secondScan);
    when(library.personalPoolCandidateIds(firstScan.stream().map(Candidate::getId).toList()))
        .thenReturn(Set.of());
    when(library.personalPoolCandidateIds(secondScan.stream().map(Candidate::getId).toList()))
        .thenReturn(Set.copyOf(secondScan.stream().map(Candidate::getId).toList()));
    when(codec.encode(any(), any())).thenReturn("next");
    var manager = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "candidateLibrary", library);
    ReflectionTestUtils.setField(manager, "cursorCodec", codec);

    var page = manager.candidates(null, null, 2, true);

    assertThat(page.getItems())
        .extracting(Candidate::getId)
        .containsExactly(secondScan.get(0).getId(), secondScan.get(1).getId());
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isEqualTo("next");
    verify(persistence)
        .findCandidates(
            null,
            actor.getUserId(),
            true,
            Set.of(),
            Set.of(),
            true,
            scannedLast.getUpdateTime(),
            scannedLast.getId(),
            101);
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot(
        "test-manager",
        "测试负责人",
        "wang-wei@example.invalid",
        "home-department",
        Set.of("support-department"),
        Set.of("managed-recruiter"),
        Set.of("explicit-department"),
        Set.of("RECRUITMENT_MANAGER"),
        Set.of("talent.read"),
        true,
        false);
  }

  private static Candidate candidate(int index, Instant updateTime) {
    var candidate = new Candidate();
    candidate.setId(String.format("%026d", index));
    candidate.setOwnerUserId("test-manager");
    candidate.setStatus("ACTIVE");
    candidate.setUpdateTime(updateTime);
    return candidate;
  }
}
