package com.ezaigc.hr.intelligence.search.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.api.MyTaskCandidateQuery;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.IndexPage;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.CurrentFactContact;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.stream.Collectors;

class TalentSearchApplicationManagerTest {

  private final ResumeFactQueryService facts = mock(ResumeFactQueryService.class);
  private final SearchIndexPort search = mock(SearchIndexPort.class);
  private final SearchIndexTarget target = new SearchIndexTarget("approved-index", 4);
  private final SearchIndexAuthorityApplicationManager authority = mock(SearchIndexAuthorityApplicationManager.class);
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final EngagementLockQueryService locks = mock(EngagementLockQueryService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final TalentSearchApplicationManager manager = new TalentSearchApplicationManager();
  private final SearchCriteria criteria = criteria();

  @Test
  void onePublicSearchPinsItsApprovedGenerationAcrossProviderAndProbePages() {
    var later = new SearchIndexTarget("later-approved-index", 5);
    when(authority.currentTarget()).thenReturn(target, later);
    when(search.search(target, criteria, null, 1)).thenReturn(new IndexPage(
        List.of(document("stale", "old")), "next", true));
    when(search.search(target, criteria, "next", 1)).thenReturn(new IndexPage(
        List.of(document("fresh-one", "current-fresh-one")), "probe", true));
    when(search.search(target, criteria, "probe", 100)).thenReturn(new IndexPage(List.of(), null, false));
    var page = manager.search(criteria, null, 1);
    assertThat(page.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-one");
    verify(authority, times(1)).currentTarget();
    verify(search, never()).search(eq(later), any(), any(), anyInt());
  }

  @Test
  void countBatchRechecksVisibilityAndFactsWithoutLoadingResultDetails() {
    doReturn(Set.of("fresh-visible", "fresh-old")).when(library).eligibleCandidateIds(any());
    when(search.search(target, criteria, null, 100)).thenReturn(new IndexPage(List.of(
        document("fresh-visible", "current-fresh-visible"),
        document("fresh-hidden", "current-fresh-hidden"),
        document("fresh-old", "old")), "next", true));

    var batch = manager.count(criteria, null);

    assertThat(batch.getCount()).isEqualTo(1);
    assertThat(batch.isHasMore()).isTrue();
    assertThat(batch.getNextCursor()).isEqualTo("next");
    verify(search, times(1)).search(eq(target), eq(criteria), any(), anyInt());
    verify(locks, never()).activeLocks(any());
  }

  @Test
  void countBatchContinuesAfterAnEntirelyExcludedPage() {
    doReturn(Set.of()).when(library).eligibleCandidateIds(any());
    when(search.search(target, criteria, null, 100)).thenReturn(new IndexPage(
        List.of(document("fresh-hidden", "current-fresh-hidden")), "next", true));
    when(search.search(target, criteria, "next", 100)).thenReturn(new IndexPage(List.of(), null, false));

    var first = manager.count(criteria, null);
    var last = manager.count(criteria, first.getNextCursor());

    assertThat(first.getCount()).isZero();
    assertThat(first.isHasMore()).isTrue();
    assertThat(last.getCount()).isZero();
    assertThat(last.isHasMore()).isFalse();
    assertThat(last.getNextCursor()).isNull();
  }

  @Test
  void countUsesPersonalPoolStageAndActorScope() {
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    criteria.setScope("PERSONAL_POOL");
    criteria.setPoolStage("PENDING_SCREENING");
    criteria.setJdId("jd-selected");
    when(search.search(target, criteria, "cursor", 100)).thenReturn(new IndexPage(
        List.of(document("fresh-mine", "current-fresh-mine")), null, false));
    when(library.personalPoolCandidateIds(List.of("fresh-mine"), "jd-selected", "PENDING_SCREENING"))
        .thenReturn(Set.of("fresh-mine"));

    assertThat(manager.count(criteria, "cursor").getCount()).isEqualTo(1);
    assertThat(criteria.getCursorActor()).startsWith("talent.search:");
    verify(authorization).requirePermission("talent.read");
    verify(library, never()).eligibleCandidateIds(any());
  }

  @Test
  void taskCountRequiresRecruitmentPermissionBeforeReadingIndex() {
    criteria.setScope("MY_TASKS");
    doThrow(new IllegalStateException("forbidden")).when(authorization).requirePermission("recruitment.manage");

    assertThatThrownBy(() -> manager.count(criteria, null)).hasMessage("forbidden");
    verify(search, never()).search(any(SearchIndexTarget.class), any(), any(), anyInt());
  }

  @Test
  void countRejectsNonAdvancingProviderCursor() {
    when(search.search(target, criteria, "same", 100)).thenReturn(new IndexPage(List.of(), "same", true));

    assertThatThrownBy(() -> manager.count(criteria, "same"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("advance");
  }

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "facts", facts);
    ReflectionTestUtils.setField(manager, "library", library);
    when(library.eligibleCandidateIds(any())).thenAnswer(invocation -> Set.copyOf((Collection<String>)invocation.getArgument(0)));
    ReflectionTestUtils.setField(manager, "search", search);
    ReflectionTestUtils.setField(manager, "authority", authority);
    when(authority.currentTarget()).thenReturn(target);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "locks", locks);
    when(facts.currentFactContacts(any()))
        .thenAnswer(invocation -> current(invocation.getArgument(0)));
    when(locks.activeLocks(any())).thenReturn(Map.of());
  }

  @Test
  void onlyLoadsFactsForEligibleHitsAndPreservesTheirOrder() {
    doReturn(Set.of("fresh-2", "fresh-1", "fresh-old")).when(library).eligibleCandidateIds(any());
    when(search.search(target, criteria, null, 10)).thenReturn(new IndexPage(List.of(
        document("fresh-2", "current-fresh-2"),
        document("fresh-excluded", "current-fresh-excluded"),
        document("fresh-old", "old"),
        document("fresh-1", "current-fresh-1")), null, false));

    var page = manager.search(criteria, null, 10);

    assertThat(page.getItems()).extracting(item -> item.getCandidateId())
        .containsExactly("fresh-2", "fresh-1");
    verify(facts).currentFactContacts(List.of("resume-fresh-2", "resume-fresh-old", "resume-fresh-1"));
    verify(facts, times(1)).currentFactContacts(any());
    verify(authorization, never()).requirePermission("talent.read");
  }

  @Test
  void excludedPagesConsumeScanBudgetWithoutLoadingFacts() {
    doReturn(Set.of()).when(library).eligibleCandidateIds(any());
    for (int index = 0; index < 8; index++) {
      when(search.search(target, criteria, index == 0 ? null : "cursor-" + index, 1))
          .thenReturn(new IndexPage(List.of(document("fresh-excluded-" + index, "old")),
              "cursor-" + (index + 1), true));
    }

    var page = manager.search(criteria, null, 1);

    assertThat(page.getItems()).isEmpty();
    assertThat(page.isScanIncomplete()).isTrue();
    assertThat(page.getNextCursor()).isEqualTo("cursor-8");
    verify(library, times(8)).eligibleCandidateIds(any());
    verify(facts, never()).currentFactContacts(any());
  }

  @Test
  void personalSearchUsesCurrentOwnershipInsteadOfSharedMembershipAndRefillsThePage() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    criteria.setScope("PERSONAL_POOL");
    when(library.personalPoolCandidateIds(any())).thenReturn(Set.of("fresh-mine"));
    when(search.search(target, criteria, null, 1)).thenReturn(new IndexPage(List.of(document("other", "current-other")), "next", true));
    when(search.search(target, criteria, "next", 1)).thenReturn(new IndexPage(List.of(document("fresh-mine", "current-fresh-mine")), null, false));
    assertThat(manager.search(criteria, null, 1).getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-mine");
    assertThat(criteria.getCursorActor()).startsWith("talent.search:");
    verify(authorization).requirePermission("talent.read");
    verify(library, never()).eligibleCandidateIds(any());
  }

  @Test
  void personalJdFilterRefillsPastCandidatesForOtherJobs() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    criteria.setScope("PERSONAL_POOL");
    criteria.setJdId("jd-selected");
    when(library.personalPoolCandidateIds(List.of("other"), "jd-selected")).thenReturn(Set.of());
    when(library.personalPoolCandidateIds(List.of("fresh-mine"), "jd-selected"))
        .thenReturn(Set.of("fresh-mine"));
    when(search.search(target, criteria, null, 1))
        .thenReturn(new IndexPage(List.of(document("other", "current-other")), "next", true));
    when(search.search(target, criteria, "next", 1))
        .thenReturn(new IndexPage(List.of(document("fresh-mine", "current-fresh-mine")), null, false));

    assertThat(manager.search(criteria, null, 1).getItems())
        .extracting(item -> item.getCandidateId()).containsExactly("fresh-mine");
    verify(library, never()).personalPoolCandidateIds(any());
  }

  @Test
  void personalSearchRechecksRoutingForStaleIndexHitsAndLookahead() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    criteria.setScope("PERSONAL_POOL");
    when(library.personalPoolCandidateIds(any())).thenReturn(Set.of("fresh-returned"));
    when(search.search(target, criteria, null, 1)).thenReturn(new IndexPage(
        List.of(document("fresh-admitted", "current-fresh-admitted")), "after-admitted", true));
    when(search.search(target, criteria, "after-admitted", 1)).thenReturn(new IndexPage(
        List.of(document("fresh-returned", "current-fresh-returned")), "after-returned", true));
    when(search.search(target, criteria, "after-returned", 100)).thenReturn(new IndexPage(
        List.of(document("fresh-terminated", "current-fresh-terminated")), null, false));

    var page = manager.search(criteria, null, 1);

    assertThat(page.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-returned");
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
    verify(library).personalPoolCandidateIds(List.of("fresh-admitted"));
    verify(library).personalPoolCandidateIds(List.of("fresh-terminated"));
    verify(locks).activeLocks(List.of("fresh-returned"));
  }

  @Test
  void returningFromScreeningIsImmediatelyVisibleWithoutReindexing() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    criteria.setScope("PERSONAL_POOL");
    when(search.search(target, criteria, null, 1)).thenReturn(new IndexPage(
        List.of(document("fresh-mine", "current-fresh-mine")), null, false));
    when(library.personalPoolCandidateIds(List.of("fresh-mine")))
        .thenReturn(Set.of(), Set.of("fresh-mine"), Set.of());

    assertThat(manager.search(criteria, null, 1).getItems()).isEmpty();
    assertThat(manager.search(criteria, null, 1).getItems())
        .extracting(item -> item.getCandidateId()).containsExactly("fresh-mine");
    assertThat(manager.search(criteria, null, 1).getItems()).isEmpty();
  }

  @Test
  void sharedJdFilterRefillsPastCandidatesForOtherJobs() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("shared-reader");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    criteria.setJdId("jd-selected");
    when(library.eligibleCandidateIds(List.of("other"), "jd-selected")).thenReturn(Set.of());
    when(library.eligibleCandidateIds(List.of("fresh-shared"), "jd-selected"))
        .thenReturn(Set.of("fresh-shared"));
    when(search.search(target, criteria, null, 1))
        .thenReturn(new IndexPage(List.of(document("other", "current-other")), "next", true));
    when(search.search(target, criteria, "next", 1))
        .thenReturn(new IndexPage(List.of(document("fresh-shared", "current-fresh-shared")), null, false));

    var page = manager.search(criteria, null, 1);
    assertThat(page.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-shared");
    assertThat(page.isHasMore()).isFalse();
    assertThat(criteria.getCursorActor()).startsWith("talent.search:");
    verify(library, never()).eligibleCandidateIds(any());
  }

  @Test
  void taskSearchRequiresRecruitmentPermissionAndOnlyReturnsCurrentAssignedTasks() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    var myTasks = mock(MyTaskCandidateQuery.class);
    ReflectionTestUtils.setField(manager, "myTasks", myTasks);
    criteria.setScope("MY_TASKS");
    when(myTasks.candidateIds(any(), any())).thenReturn(Set.of("fresh-assigned"));
    when(search.search(target, criteria, null, 10)).thenReturn(new IndexPage(List.of(document("fresh-assigned", "current-fresh-assigned"), document("other", "current-other")), null, false));
    assertThat(manager.search(criteria, null, 10).getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-assigned");
    verify(authorization).requirePermission("talent.read");
    verify(authorization).requirePermission("recruitment.manage");
    verify(library, never()).eligibleCandidateIds(any());
  }

  @Test
  void taskJobScopeRefillsBeforePaginationAndRechecksLookaheadFacts() {
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("my-user");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    var myTasks = mock(MyTaskCandidateQuery.class);
    ReflectionTestUtils.setField(manager, "myTasks", myTasks);
    criteria.setScope("MY_TASKS");
    criteria.setJdId("jd-selected");
    when(myTasks.candidateIds(List.of("fresh-other-job"), "jd-selected")).thenReturn(Set.of());
    when(myTasks.candidateIds(List.of("fresh-assigned"), "jd-selected"))
        .thenReturn(Set.of("fresh-assigned"));
    when(myTasks.candidateIds(List.of("fresh-old-facts", "fresh-completed"), "jd-selected"))
        .thenReturn(Set.of("fresh-old-facts"));
    when(search.search(target, criteria, null, 1)).thenReturn(new IndexPage(
        List.of(document("fresh-other-job", "current-fresh-other-job")), "after-other", true));
    when(search.search(target, criteria, "after-other", 1)).thenReturn(new IndexPage(
        List.of(document("fresh-assigned", "current-fresh-assigned")), "after-assigned", true));
    when(search.search(target, criteria, "after-assigned", 100)).thenReturn(new IndexPage(
        List.of(document("fresh-old-facts", "old"), document("fresh-completed", "current-fresh-completed")),
        null, false));

    var page = manager.search(criteria, null, 1);

    assertThat(page.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-assigned");
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
    assertThat(criteria.getCursorActor()).startsWith("talent.search:");
    verify(myTasks).candidateIds(List.of("fresh-old-facts", "fresh-completed"), "jd-selected");
    verify(library, never()).personalPoolCandidateIds(any(), any());
    verify(library, never()).eligibleCandidateIds(any(), any());
  }

  @Test
  void explainsAnyDegreeAndCityOnceWithoutReportingUnselectedAlternativesAsFailures() {
    var candidate = document("fresh-1", "current-fresh-1");
    candidate.setDegrees(List.of("BACHELOR", "Diploma"));
    candidate.setSkills(List.of("Java", "SQL"));
    criteria.setDegrees(List.of("本科", "硕士"));
    criteria.setCities(List.of("上海", "北京"));
    criteria.setSkills(List.of("Java", "SQL"));
    when(search.search(target, criteria, null, 10)).thenReturn(new IndexPage(List.of(candidate), null, false));
    var result = manager.search(criteria, null, 10).getItems().getFirst();
    assertThat(result.getConditionMatches()).filteredOn(item -> "DEGREE".equals(item.getCode()))
        .singleElement().satisfies(item -> {
          assertThat(item.isMatched()).isTrue();
          assertThat(item.getExpected()).isEqualTo("本科 / 硕士（任一）");
          assertThat(item.getMatchedValues()).containsExactly("BACHELOR");
        });
    assertThat(result.getConditionMatches()).filteredOn(item -> "CITY".equals(item.getCode()))
        .singleElement().satisfies(item -> assertThat(item.getMatchedValues()).containsExactly("上海"));
    assertThat(result.getConditionMatches()).filteredOn(item -> "SKILL".equals(item.getCode())).hasSize(2);
  }

  @Test
  void refillsPastStaleHitsWithoutConsumingTheNextValidResult() {
    SearchDocument staleOne = document("stale-1", "old");
    SearchDocument freshOne = document("fresh-1", "current-fresh-1");
    SearchDocument freshTwo = document("fresh-2", "current-fresh-2");
    SearchDocument staleTwo = document("stale-2", "old");
    when(search.search(target, criteria, null, 2))
        .thenReturn(new IndexPage(List.of(staleOne, freshOne), "cursor-1", true));
    when(search.search(target, criteria, "cursor-1", 1))
        .thenReturn(new IndexPage(List.of(freshTwo), "cursor-2", true));
    when(search.search(target, criteria, "cursor-2", 100))
        .thenReturn(new IndexPage(List.of(staleTwo), null, false));

    var page = manager.search(criteria, null, 2);

    assertThat(page.getItems())
        .extracting(item -> item.getCandidateId())
        .containsExactly("fresh-1", "fresh-2");
    assertThat(page.getItems())
        .extracting(item -> item.getEmail())
        .containsExactly("fresh-1@example.com", "fresh-2@example.com");
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
    verify(search).search(target, criteria, "cursor-1", 1);
    verify(search).search(target, criteria, "cursor-2", 100);
    verify(locks).activeLocks(List.of("fresh-1", "fresh-2"));
    verify(locks, never()).activeLock(any());
  }

  @Test
  void skipsInProcessCandidatesBeforeFillingSearchPage() {
    doReturn(Set.of("fresh-shared")).when(library).eligibleCandidateIds(any());
    when(search.search(target, criteria, null, 1)).thenReturn(new IndexPage(List.of(document("fresh-interviewing", "current-fresh-interviewing")), "after-active", true));
    when(search.search(target, criteria, "after-active", 1)).thenReturn(new IndexPage(List.of(document("fresh-shared", "current-fresh-shared")), null, false));
    var page=manager.search(criteria,null,1);
    assertThat(page.getItems()).extracting(item->item.getCandidateId()).containsExactly("fresh-shared");
    assertThat(page.isHasMore()).isFalse();
  }

  @Test
  void keepsTheCursorAtTheLastReturnedResultWhenLookaheadFindsMore() {
    SearchDocument first = document("fresh-1", "current-fresh-1");
    SearchDocument second = document("fresh-2", "current-fresh-2");
    SearchDocument third = document("fresh-3", "current-fresh-3");
    when(search.search(target, criteria, null, 2))
        .thenReturn(new IndexPage(List.of(first, second), "cursor-after-second", true));
    when(search.search(target, criteria, "cursor-after-second", 100))
        .thenReturn(new IndexPage(List.of(third), null, false));

    var page = manager.search(criteria, null, 2);

    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isEqualTo("cursor-after-second");
    assertThat(page.getItems())
        .extracting(item -> item.getCandidateId())
        .containsExactly("fresh-1", "fresh-2");
  }

  @Test
  void manyStalePagesReturnEmptyPartialPageWithUsableContinuation() {
    for (int index = 0; index < 8; index++) {
      when(search.search(target, criteria, index == 0 ? null : "cursor-" + index, 1))
          .thenReturn(new IndexPage(List.of(document("stale-" + index, "old")),
              "cursor-" + (index + 1), true));
    }
    when(search.search(target, criteria, "cursor-8", 1))
        .thenReturn(new IndexPage(List.of(document("fresh-1", "current-fresh-1")), null, false));

    var partial = manager.search(criteria, null, 1);

    assertThat(partial.getItems()).isEmpty();
    assertThat(partial.isScanIncomplete()).isTrue();
    assertThat(partial.isHasMore()).isTrue();
    assertThat(partial.getNextCursor()).isEqualTo("cursor-8");
    verify(search, times(8)).search(eq(target), eq(criteria), any(), anyInt());
    var continued = manager.search(criteria, partial.getNextCursor(), 1);
    assertThat(continued.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-1");
    assertThat(continued.isScanIncomplete()).isFalse();
    assertThat(continued.isHasMore()).isFalse();
    assertThat(continued.getNextCursor()).isNull();
  }

  @Test
  void fillAndLookaheadShareBudgetAndContinuationSkipsOnlyProvenStalePages() {
    when(search.search(target, criteria, null, 1))
        .thenReturn(new IndexPage(List.of(document("fresh-1", "current-fresh-1")), "cursor-1", true));
    for (int index = 1; index < 8; index++) {
      when(search.search(target, criteria, "cursor-" + index, 100))
          .thenReturn(new IndexPage(List.of(document("stale-" + index, "old")),
              "cursor-" + (index + 1), true));
    }
    when(search.search(target, criteria, "cursor-8", 1))
        .thenReturn(new IndexPage(List.of(document("fresh-2", "current-fresh-2")), null, false));

    var partial = manager.search(criteria, null, 1);

    assertThat(partial.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-1");
    assertThat(partial.isScanIncomplete()).isTrue();
    assertThat(partial.getNextCursor()).isEqualTo("cursor-8");
    verify(search, times(8)).search(eq(target), eq(criteria), any(), anyInt());
    var continued = manager.search(criteria, partial.getNextCursor(), 1);
    assertThat(continued.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-2");
  }

  @Test
  void lookaheadAdvancesPastStalePageButNeverPastPageContainingFreshCandidate() {
    when(search.search(target, criteria, null, 1))
        .thenReturn(new IndexPage(List.of(document("fresh-1", "current-fresh-1")), "after-first", true));
    when(search.search(target, criteria, "after-first", 100))
        .thenReturn(new IndexPage(List.of(document("stale", "old")), "after-stale", true));
    when(search.search(target, criteria, "after-stale", 100))
        .thenReturn(new IndexPage(List.of(document("fresh-2", "current-fresh-2")), "after-second", true));
    when(search.search(target, criteria, "after-stale", 1))
        .thenReturn(new IndexPage(List.of(document("fresh-2", "current-fresh-2")), null, false));

    var page = manager.search(criteria, null, 1);

    assertThat(page.isScanIncomplete()).isFalse();
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isEqualTo("after-stale");
    var continued = manager.search(criteria, page.getNextCursor(), 1);
    assertThat(continued.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-2");
  }

  @Test
  void terminalPageAtBudgetBoundaryIsCompleteRatherThanPartial() {
    for (int index = 0; index < 8; index++) {
      when(search.search(target, criteria, index == 0 ? null : "cursor-" + index, 1))
          .thenReturn(new IndexPage(List.of(document("stale-" + index, "old")),
              index == 7 ? null : "cursor-" + (index + 1), index < 7));
    }

    var page = manager.search(criteria, null, 1);

    assertThat(page.getItems()).isEmpty();
    assertThat(page.isScanIncomplete()).isFalse();
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
    verify(search, times(8)).search(eq(target), eq(criteria), any(), anyInt());
  }

  @Test
  void partiallyFilledPageContinuesWithoutRepeatingAlreadyReturnedCandidate() {
    when(search.search(target, criteria, null, 2)).thenReturn(new IndexPage(
        List.of(document("fresh-1", "current-fresh-1"), document("stale-0", "old")), "cursor-1", true));
    for (int index = 1; index < 8; index++) {
      when(search.search(target, criteria, "cursor-" + index, 1))
          .thenReturn(new IndexPage(List.of(document("stale-" + index, "old")),
              "cursor-" + (index + 1), true));
    }
    when(search.search(target, criteria, "cursor-8", 2)).thenReturn(new IndexPage(
        List.of(document("fresh-2", "current-fresh-2")), null, false));

    var partial = manager.search(criteria, null, 2);

    assertThat(partial.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-1");
    assertThat(partial.isScanIncomplete()).isTrue();
    assertThat(partial.getNextCursor()).isEqualTo("cursor-8");
    verify(search, times(8)).search(eq(target), eq(criteria), any(), anyInt());
    var continued = manager.search(criteria, partial.getNextCursor(), 2);
    assertThat(continued.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-2");
    assertThat(continued.isScanIncomplete()).isFalse();
  }

  @Test
  void freshLookaheadAtLastAllowedCallKeepsItsPageForContinuation() {
    when(search.search(target, criteria, null, 1))
        .thenReturn(new IndexPage(List.of(document("fresh-1", "current-fresh-1")), "cursor-1", true));
    for (int index = 1; index < 7; index++) {
      when(search.search(target, criteria, "cursor-" + index, 100))
          .thenReturn(new IndexPage(List.of(document("stale-" + index, "old")),
              "cursor-" + (index + 1), true));
    }
    when(search.search(target, criteria, "cursor-7", 100))
        .thenReturn(new IndexPage(List.of(document("fresh-2", "current-fresh-2")), null, false));

    var page = manager.search(criteria, null, 1);

    assertThat(page.isScanIncomplete()).isFalse();
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isEqualTo("cursor-7");
    assertThat(page.getItems()).extracting(item -> item.getCandidateId()).containsExactly("fresh-1");
    verify(search, times(8)).search(eq(target), eq(criteria), any(), anyInt());
  }

  private static Map<String, CurrentFactContact> current(Collection<String> resumeVersionIds) {
    return resumeVersionIds.stream()
        .filter(id -> id.startsWith("resume-fresh"))
        .collect(
            Collectors.toUnmodifiableMap(
                id -> id,
                id ->
                    new CurrentFactContact(
                        "current-" + id.substring("resume-".length()),
                        id.substring("resume-".length()) + "@example.com",
                        "13800138000")));
  }

  private static SearchDocument document(String candidateId, String snapshotId) {
    return new SearchDocument(
        candidateId,
        "resume-" + candidateId,
        snapshotId,
        candidateId,
        "工程师",
        "上海",
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        BigDecimal.ONE,
        null,
        null,
        null,
        null,
        candidateId,
        Instant.parse("2026-09-03T00:00:00Z"));
  }

  private static SearchCriteria criteria() {
    return new SearchCriteria(
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
