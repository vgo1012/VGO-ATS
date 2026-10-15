package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobBasisRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultSummaryRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class MatchJobApplicationManagerTest {
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final MatchRunPersistenceService runs = mock(MatchRunPersistenceService.class);
  private final MatchJobApplicationManager manager = new MatchJobApplicationManager();
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot("hr", "招聘", "hr@example.invalid",
      "home", Set.of("support"), Set.of("report"), Set.of("explicit"), Set.of("RECRUITER"),
      Set.of("matching.run"), false, false);

  @BeforeEach void setup() {
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "runs", runs);
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("match-job-test-signing-key-32-bytes"));
    ReflectionTestUtils.setField(manager, "json", JsonMapper.builder().build());
    when(auth.currentActor()).thenReturn(actor);
    when(runs.findJobRevision(any())).thenReturn("revision-one");
  }

  @Test void appliesScopeBeforeAggregatingAndPaginatesOneExtraRow() {
    when(runs.findJobs(any())).thenReturn(List.of(job("3"), job("2"), job("1")));
    var page = manager.jobs(null, null, 2);
    assertThat(page.getItems()).hasSize(2);
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isNotBlank();
    verify(runs).findJobRevision(any());
    var captor = ArgumentCaptor.forClass(MatchJobQuery.class);
    verify(runs).findJobs(captor.capture());
    var query = captor.getValue();
    assertThat(query.getLimit()).isEqualTo(3);
    assertThat(query.getActorId()).isEqualTo("hr");
    assertThat(query.getManagedUserIds()).isEmpty();
    assertThat(query.getDepartmentIds()).isEmpty();
    assertThat(query.isCompanyScope()).isFalse();
    assertThatThrownBy(() -> manager.jobs("other-job", page.getNextCursor(), 2)).isInstanceOf(PlatformException.class);
    when(runs.findJobRevision(any())).thenReturn("revision-two");
    assertThatThrownBy(() -> manager.jobs(null, page.getNextCursor(), 2)).isInstanceOf(PlatformException.class);
  }

  @Test void exactJobFirstPageSkipsUnusedRevisionForSingleOrEmptyResult() {
    when(runs.findJobs(any())).thenReturn(List.of(job("1")), List.of());

    var single = manager.jobs("job", null, 20);
    var empty = manager.jobs("missing", null, 20);

    assertThat(single.getItems()).hasSize(1);
    assertThat(single.getNextCursor()).isNull();
    assertThat(empty.getItems()).isEmpty();
    assertThat(empty.getNextCursor()).isNull();
    verify(runs, never()).findJobRevision(any());
  }

  @Test void exactJobInputCursorStillChecksRevisionBeforeReadingTheLastPage() {
    var codec = new SignedCursorCodec("match-job-test-signing-key-32-bytes");
    String scope = CursorScopes.scope("matching.jobs", "hr", false, List.of(),
        List.of(), "job", null, "revision-one");
    String cursor = new StableCursor(Instant.EPOCH, "1".repeat(26)).encode(codec, scope);
    when(runs.findJobs(any())).thenReturn(List.of());

    var page = manager.jobs("job", cursor, 20);

    assertThat(page.getItems()).isEmpty();
    verify(runs).findJobRevision(any());
  }

  @Test void resultPreservesActualSourceAndCursorRejectsChangedActor() {
    var run = new MatchRun(); run.setId("basis"); run.setJdVersionId("job"); run.setOwnerUserId("hr");
    when(runs.findRun("basis")).thenReturn(run);
    var first = result("actual-run-1", "candidate-1", 1);
    var second = result("actual-run-2", "candidate-2", 2);
    when(runs.findJobResults(any())).thenReturn(List.of(first, second));
    var page = manager.results("job", "basis", null, 1);
    assertThat(page.getItems().getFirst().getRunId()).isEqualTo("actual-run-1");
    assertThat(page.getItems().getFirst().getResult().getResultId()).isEqualTo("result-candidate-1");
    assertThat(page.getItems().getFirst().getResult().getBaseEvaluationId()).isEqualTo("base-candidate-1");
    assertThat(page.getItems().getFirst().getResult().getStableRank()).isEqualTo(1);
    actor.setUserId("other-hr");
    assertThatThrownBy(() -> manager.results("job", "basis", page.getNextCursor(), 1))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.results("other-job", "basis", null, 1))
        .isInstanceOf(PlatformException.class);
  }

  @Test void deniesReadBeforeDatabaseAndDoesNotExposeUnauthorizedBasis() {
    doThrow(PlatformExceptions.forbidden("DENIED", "禁止访问")).when(auth).requirePermission("matching.run");
    assertThatThrownBy(() -> manager.jobs(null, null, 50)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.resultsSummary("job", "basis")).isInstanceOf(PlatformException.class);
    verifyNoInteractions(runs);
  }

  @Test void unauthorizedRepresentativeCannotBeUsedToReadAnotherPersonsBasis() {
    var run = new MatchRun(); run.setId("secret"); run.setJdVersionId("job"); run.setOwnerUserId("another-hr");
    when(runs.findRun("secret")).thenReturn(run);
    doThrow(PlatformExceptions.forbidden("DENIED", "禁止访问")).when(auth).requireAccess(any(), any());
    assertThatThrownBy(() -> manager.results("job", "secret", null, 50)).isInstanceOf(PlatformException.class);
    verify(runs, never()).findJobRevision(any());
    verify(runs, never()).findJobResults(any());
  }

  @Test void currentCandidateOwnerCanReadOnlyTheServerScopedBasis() {
    var run = new MatchRun(); run.setId("leader-run"); run.setJdVersionId("job");
    run.setOwnerUserId("leader");
    when(runs.findRun("leader-run")).thenReturn(run);
    doThrow(PlatformExceptions.forbidden("DENIED", "禁止访问")).when(auth).requireAccess(any(), any());
    when(runs.countVisibleJobBasis(any())).thenReturn(1);
    var summary = new MatchJobResultSummaryRow(); summary.setTotal(1);
    when(runs.findJobResultsSummary(any())).thenReturn(summary);

    assertThat(manager.resultsSummary("job", "leader-run").getTotal()).isEqualTo(1);
    var captor = ArgumentCaptor.forClass(MatchJobQuery.class);
    verify(runs).countVisibleJobBasis(captor.capture());
    assertThat(captor.getValue().getActorId()).isEqualTo("hr");
    assertThat(captor.getValue().getBasisRunId()).isEqualTo("leader-run");
  }

  @Test void basisIncludesFrozenReadableWeightsAndPersonalCriteria() {
    var row = new MatchJobBasisRow(); row.setBasisRunId("1".repeat(26)); row.setLastRunAt(Instant.EPOCH);
    row.setSkillsWeight(30); row.setSelectedCriterionIdsJson("[\"degree\"]");
    row.setPersonalScreeningSettingsJson("{\"criteria\":[{\"code\":\"EDUCATION\"}],\"defaultSortMode\":\"REFERENCE_SCORE\"}");
    when(runs.findJobBases(any())).thenReturn(List.of(row));
    var basis = manager.bases("job", null, 50).getItems().getFirst();
    assertThat(basis.getSkillsWeight()).isEqualTo(30);
    assertThat(basis.getSelectedCriterionIds()).containsExactly("degree");
    assertThat(basis.getPersonalScreeningSettings().path("criteria").get(0).path("code").asString()).isEqualTo("EDUCATION");
  }

  @Test void summarizesAllVisibleResultsWithoutReadingAPageOrRevision() {
    var run = new MatchRun(); run.setId("basis"); run.setJdVersionId("job"); run.setOwnerUserId("hr");
    when(runs.findRun("basis")).thenReturn(run);
    var row = new MatchJobResultSummaryRow(); row.setTotal(41); row.setStrong(10);
    row.setConsider(20); row.setReject(10); row.setMissing(1);
    when(runs.findJobResultsSummary(any())).thenReturn(row);
    var summary = manager.resultsSummary("job", "basis");
    assertThat(summary.getTotal()).isEqualTo(41);
    assertThat(summary.getStrong() + summary.getConsider() + summary.getReject() + summary.getMissing()).isEqualTo(41);
    var captor = ArgumentCaptor.forClass(MatchJobQuery.class);
    verify(runs).findJobResultsSummary(captor.capture());
    assertThat(captor.getValue().getBasisRunId()).isEqualTo("basis");
    assertThat(captor.getValue().getActorId()).isEqualTo("hr");
    assertThat(captor.getValue().getManagedUserIds()).isEmpty();
    verify(auth).requireAccess(any(), any());
    verify(runs, never()).findJobResults(any());
    verify(runs, never()).findJobRevision(any());
  }

  @Test void summaryRejectsMissingOrMismatchedBasisAndUnauthorizedAccess() {
    assertThatThrownBy(() -> manager.resultsSummary("job", "missing")).isInstanceOf(PlatformException.class);
    var run = new MatchRun(); run.setId("basis"); run.setJdVersionId("other");
    when(runs.findRun("basis")).thenReturn(run);
    assertThatThrownBy(() -> manager.resultsSummary("job", "basis")).isInstanceOf(PlatformException.class);
    run.setJdVersionId("job");
    doThrow(PlatformExceptions.forbidden("DENIED", "禁止访问")).when(auth).requireAccess(any(), any());
    assertThatThrownBy(() -> manager.resultsSummary("job", "basis")).isInstanceOf(PlatformException.class);
    verify(runs, never()).findJobResultsSummary(any());
  }

  private MatchJobRow job(String character) {
    var row = new MatchJobRow(); row.setJdVersionId(character.repeat(26)); row.setLastRunAt(Instant.EPOCH);
    return row;
  }
  private MatchJobResultRow result(String runId, String candidate, long rank) {
    var row = new MatchJobResultRow(); row.setMatchRunId(runId); row.setCandidateId(candidate);
    row.setId("result-" + candidate); row.setBaseEvaluationId("base-" + candidate);
    row.setProfessionalScore(BigDecimal.TEN); row.setReferenceScore(BigDecimal.TEN);
    row.setStableRank(27); row.setWorkspaceRank(rank); row.setResultJson("{}");
    return row;
  }
}
