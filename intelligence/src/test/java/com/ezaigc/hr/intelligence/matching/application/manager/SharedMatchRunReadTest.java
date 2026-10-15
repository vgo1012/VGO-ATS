package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.entity.BaseEvaluation;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightedResult;
import com.ezaigc.hr.intelligence.matching.service.BaseEvaluationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class SharedMatchRunReadTest {
  private final MatchRunApplicationManager manager = new MatchRunApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final MatchRunPersistenceService runs = mock(MatchRunPersistenceService.class);
  private final WeightedResultPersistenceService results = mock(WeightedResultPersistenceService.class);
  private final SelectionItemPersistenceService selections = mock(SelectionItemPersistenceService.class);
  private final BaseEvaluationPersistenceService bases = mock(BaseEvaluationPersistenceService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final MatchRun run = new MatchRun();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", runs);
    ReflectionTestUtils.setField(manager, "weightedResultPersistenceService", results);
    ReflectionTestUtils.setField(manager, "selectionItemPersistenceService", selections);
    ReflectionTestUtils.setField(manager, "baseEvaluationPersistenceService", bases);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("shared-matching-test-signing-key-32-bytes"));
    ReflectionTestUtils.setField(manager, "candidateLibrary", library);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(
        "reader", "Reader", null, "other-dept", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false));
    doThrow(new PlatformException(HttpStatus.FORBIDDEN, "FORBIDDEN", "denied", false))
        .when(authorization).requireAccess(eq("matching.run"), any());
    run.setId("run");
    run.setSelectionSnapshotId("selection");
    run.setOwnerUserId("owner");
    when(runs.findRun("run")).thenReturn(run);
    when(selections.findSelectionItems("selection")).thenReturn(List.of(
        new SelectionItem("selection", 1, "shared", "shared-resume", "shared-fact"),
        new SelectionItem("selection", 2, "private", "private-resume", "private-fact")));
    when(library.eligibleCandidateIds(any())).thenAnswer(call -> {
      Collection<String> candidates = call.getArgument(0);
      return candidates.stream().filter("shared"::equals).collect(Collectors.toSet());
    });
    when(results.findResultsUnsorted("run")).thenReturn(List.of(result("private", 1), result("shared", 2)));
  }

  @Test
  void sharedCandidateMakesItsRunReadableWithoutRoleOrOwnership() {
    assertThat(manager.get("run")).isSameAs(run);
  }

  @Test
  void mixedRunReturnsOnlySharedCandidateAndDoesNotLeakPrivatePage() {
    var page = manager.results("run", null, 1);
    assertThat(page.getItems()).extracting(value -> value.getCandidateId()).containsExactly("shared");
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
  }

  @Test
  void sharedPaginationSkipsPrivateRowsBeforeBuildingPageAndCursor() {
    doAnswer(call -> {
      Collection<String> candidates = call.getArgument(0);
      return candidates.stream().filter(Set.of("shared", "shared-next")::contains).collect(Collectors.toSet());
    }).when(library).eligibleCandidateIds(any());
    when(selections.findSelectionItems("selection")).thenReturn(List.of(
        new SelectionItem("selection", 1, "shared", "shared-resume", "shared-fact"),
        new SelectionItem("selection", 2, "private", "private-resume", "private-fact"),
        new SelectionItem("selection", 3, "shared-next", "next-resume", "next-fact")));
    when(results.findResultsUnsorted("run")).thenReturn(List.of(
        result("private", 1), result("shared", 2), result("private-next", 3), result("shared-next", 4)));
    var first = manager.results("run", null, 1);
    assertThat(first.getItems()).extracting(value -> value.getCandidateId()).containsExactly("shared");
    assertThat(first.isHasMore()).isTrue();
    var second = manager.results("run", first.getNextCursor(), 1);
    assertThat(second.getItems()).extracting(value -> value.getCandidateId()).containsExactly("shared-next");
    assertThat(second.isHasMore()).isFalse();
  }

  @Test
  void sharedReadNeverGrantsCancelOrRetryWhileCandidateRemainsShared() {
    assertThatThrownBy(() -> manager.cancel("run", "reason")).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.retry("run", "reason")).isInstanceOf(PlatformException.class);
    verifyNoInteractions(tasks);
  }

  @Test
  void sharedBaseIsReadableButAnotherCandidateBaseInSameRunIsNot() {
    for (String candidate : List.of("shared", "private")) {
      BaseEvaluation base = new BaseEvaluation();
      base.setId(candidate + "-base");
      base.setDimensionsJson("[]");
      base.setCriteriaJson("[]");
      when(bases.findBase(base.getId())).thenReturn(base);
      when(runs.findRunByBase(base.getId())).thenReturn(run);
    }
    assertThat(manager.base("shared-base").getBaseEvaluationId()).isEqualTo("shared-base");
    assertThatThrownBy(() -> manager.base("private-base")).isInstanceOf(PlatformException.class);
  }

  @Test
  void leavingSharedLibraryRevokesReadAndNeverGrantsWrite() {
    doReturn(Set.of()).when(library).eligibleCandidateIds(any());
    assertThatThrownBy(() -> manager.get("run")).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.results("run", null, 20)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.cancel("run", "reason")).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.retry("run", "reason")).isInstanceOf(PlatformException.class);
    verifyNoInteractions(tasks);
  }

  private static WeightedResult result(String candidate, int rank) {
    WeightedResult value = new WeightedResult();
    value.setId(candidate + "-result");
    value.setBaseEvaluationId(candidate + "-base");
    value.setCandidateId(candidate);
    value.setStableRank(rank);
    value.setResultJson("{}");
    return value;
  }
}
