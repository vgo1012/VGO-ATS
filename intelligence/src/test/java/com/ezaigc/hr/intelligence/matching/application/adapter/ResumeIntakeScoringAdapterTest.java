package com.ezaigc.hr.intelligence.matching.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.intelligence.matching.application.manager.AiMatchingEvaluationManager;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ResumeIntakeScoringAdapterTest {
  private final BackgroundActorExecutor actors = mock(BackgroundActorExecutor.class);
  private final MatchRunApplicationManager matching = mock(MatchRunApplicationManager.class);
  private final AiMatchingEvaluationManager evaluations = mock(AiMatchingEvaluationManager.class);
  private final ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
  private final SelectionItemPersistenceService selectionItems = mock(SelectionItemPersistenceService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final ResumeIntakeScoringAdapter adapter = new ResumeIntakeScoringAdapter();
  private final MatchRun run = new MatchRun();

  ResumeIntakeScoringAdapterTest() {
    ReflectionTestUtils.setField(adapter, "actors", actors);
    ReflectionTestUtils.setField(adapter, "matching", matching);
    ReflectionTestUtils.setField(adapter, "evaluations", evaluations);
    ReflectionTestUtils.setField(adapter, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(adapter, "selectionItems", selectionItems);
    ReflectionTestUtils.setField(adapter, "tasks", tasks);
    ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
    ReflectionTestUtils.setField(adapter, "clock",
        Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC));
    when(actors.execute(anyString(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
    run.setId("run");
    run.setTaskId("task");
    when(matching.createForIntake("jd", "resume")).thenReturn(run);
    when(matching.get("run")).thenReturn(run);
    when(tasks.get("task")).thenReturn(task("QUEUED", null, false));
  }

  @Test
  void returnsQueuedTaskAndExecutesWithPersistedRequesterIdentity() {
    var result = adapter.enqueue("jd", "resume", "requester");
    assertThat(result.status()).isEqualTo("QUEUED");
    assertThat(result.runId()).isEqualTo("run");
    verify(actors).execute(anyString(), any());
    verify(matching).createForIntake("jd", "resume");
  }

  @Test
  void concurrentUniqueConflictRereadsWinningRunAfterFailedCallReturns() {
    when(matching.createForIntake("jd", "resume"))
        .thenThrow(new DuplicateKeyException("synthetic race")).thenReturn(run);
    assertThat(adapter.enqueue("jd", "resume", "requester").runId()).isEqualTo("run");
    verify(matching, times(2)).createForIntake("jd", "resume");
  }

  @Test
  void unavailableIdentityDoesNotStartScoring() {
    doThrow(PlatformExceptions.forbidden("ACCOUNT_DISABLED", "请求人账号不可用"))
        .when(actors).execute(anyString(), any());
    assertThatThrownBy(() -> adapter.enqueue("jd", "resume", "requester"))
        .hasMessage("请求人账号不可用");
    verifyNoInteractions(matching, tasks);
  }

  @Test
  void exposesFailureAndRetriesExistingRunWithoutReparsingOrNewScoringRun() {
    when(tasks.get("task")).thenReturn(task("FAILED", "MATCH_FAILED", true));
    var failed = adapter.get("run", "requester");
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(failed.errorCode()).isEqualTo("MATCH_FAILED");
    assertThat(failed.retryable()).isTrue();
    when(tasks.get("task")).thenReturn(task("QUEUED", null, false));
    assertThat(adapter.retry("run", "requester").status()).isEqualTo("QUEUED");
    verify(matching).retry("run", "重试导入关联 JD 的评分");
  }

  @Test
  void preparesOnlyFrozenProfessionalJdFactsForTheCombinedCall() {
    var jd = new JdFactSnapshotData();
    jd.setSnapshotId("jd-snapshot");
    jd.setProfessional(new JdFactSnapshotData.ProfessionalFacts(
        List.of(), List.of(), null, null, List.of("模型部署"), List.of(), List.of()));
    when(matching.requireIntakeScoringReady("jd")).thenReturn(jd);

    var prepared = adapter.prepare("jd", "requester");

    assertThat(prepared.jdFactSnapshotId()).isEqualTo("jd-snapshot");
    assertThat(prepared.jdFactsJson()).contains("professional", "模型部署")
        .doesNotContain("controlled", "fulfillment");
    assertThat(prepared.promptVersion()).isEqualTo(AiScoringConstants.PROMPT_VERSION);
    assertThat(prepared.schemaVersion()).isEqualTo(AiScoringConstants.SCHEMA_VERSION);
    assertThat(prepared.evaluatedOn()).isEqualTo(LocalDate.of(2026, 9, 21));
    verify(matching).requireIntakeScoringReady("jd");
  }

  @Test
  void importsCombinedAssessmentBeforeCreatingTheNormalMatchRun() {
    var jd = new JdFactSnapshotData();
    jd.setSnapshotId("jd-snapshot");
    var resume = new ResumeFactSnapshotData();
    resume.setSnapshotId("resume-snapshot");
    var assessment = new PrecomputedAssessment("jd-snapshot",
        AiScoringConstants.PROMPT_VERSION, AiScoringConstants.SCHEMA_VERSION,
        LocalDate.of(2026, 9, 21), "provider", "model", "{}", 10, 5, 20);
    when(matching.requireIntakeScoringReady("jd")).thenReturn(jd);
    when(resumeFacts.currentFactBatch(List.of("resume"))).thenReturn(id -> resume);
    when(evaluations.importPrecomputed(jd, resume, assessment)).thenReturn(true);

    assertThat(adapter.enqueue("jd", "resume", "requester", assessment).runId())
        .isEqualTo("run");

    InOrder order = inOrder(matching, evaluations);
    order.verify(matching).requireIntakeScoringReady("jd");
    order.verify(evaluations).importPrecomputed(jd, resume, assessment);
    order.verify(matching).createForIntake("jd", "resume");
  }

  @Test
  void readsOnlyTheExactResumeFactFrozenByThePreviousCompanyRun() {
    run.setJdVersionId("jd");
    run.setRunPurpose("COMPANY_DEFAULT");
    run.setSelectionSnapshotId("selection-1");
    SelectionItem item = new SelectionItem();
    item.setResumeVersionId("resume");
    item.setResumeFactSnapshotId("fact-old");
    when(selectionItems.findSelectionItems("selection-1")).thenReturn(List.of(item));

    assertThat(adapter.scoredFactSnapshotId("run", "jd", "resume", "requester"))
        .isEqualTo("fact-old");
    verify(actors).execute(anyString(), any());
    verify(matching).get("run");
  }

  @Test
  void rejectsACompanyRunForAnotherJobOrResume() {
    run.setJdVersionId("other-jd");
    run.setRunPurpose("COMPANY_DEFAULT");
    run.setSelectionSnapshotId("selection-1");

    assertThatThrownBy(() -> adapter.scoredFactSnapshotId("run", "jd", "resume", "requester"))
        .hasMessageContaining("评分");
    verifyNoInteractions(selectionItems);

    run.setJdVersionId("jd");
    SelectionItem other = new SelectionItem();
    other.setResumeVersionId("other-resume");
    when(selectionItems.findSelectionItems("selection-1")).thenReturn(List.of(other));
    assertThatThrownBy(() -> adapter.scoredFactSnapshotId("run", "jd", "resume", "requester"))
        .hasMessageContaining("评分");
  }

  private TaskSnapshot task(String status, String error, boolean retryable) {
    return new TaskSnapshot("task", "MATCH_RUN", "MATCH_RUN", "run", "requester", "requester",
        "department", "matching.run", "matching.run", status, 1, 3, 0, error, null, retryable,
        null, 0L, null, null, null, null);
  }
}
