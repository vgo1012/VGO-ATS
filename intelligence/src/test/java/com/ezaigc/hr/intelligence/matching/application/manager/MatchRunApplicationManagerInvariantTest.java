package com.ezaigc.hr.intelligence.matching.application.manager;

import com.ezaigc.hr.talent.api.CandidateDirectoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateMatchRunDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightedResult;
import com.ezaigc.hr.intelligence.matching.service.CandidateSelectionSnapshotPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunItemOutcomePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfilePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.http.HttpStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;

class MatchRunApplicationManagerInvariantTest {

  // ee5ec5933 创建指纹：固定请求字段的旧格式快照，不随生产算法更新。
  private static final String LEGACY_REFERENCE_FINGERPRINT =
      "aa0966b292e246a45b800e7fddd4a7ca864c550eccbbc76cc7d4cace88d140db";
  private static final String LEGACY_BONUS_FINGERPRINT =
      "9fee454a7fec460141658910ccc05d96b1912c523ed0d7d9070058d6cdd3bbbb";
  private static final String LEGACY_PROFESSIONAL_FINGERPRINT =
      "d4464cf06218cc254ec1c5de715e5f8e91a9f60b4f0e576173f4ad8a488810c2";

  @Test
  void candidateOwnerReadsOnlyTheirResultFromALeadersRun() {
    var manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    var runs = mock(MatchRunPersistenceService.class);
    var selections = mock(SelectionItemPersistenceService.class);
    var results = mock(WeightedResultPersistenceService.class);
    var authorization = mock(AuthorizationService.class);
    var run = new MatchRun(); run.setId("leader-run"); run.setJdVersionId("job");
    run.setOwnerUserId("leader"); run.setSelectionSnapshotId("selection");
    when(runs.findRun("leader-run")).thenReturn(run);
    when(runs.findOwnedPendingResultCandidateIds("leader-run", "owner")).thenReturn(List.of("mine"));
    when(selections.findSelectionItems("selection")).thenReturn(List.of());
    var actor = new CurrentActorSnapshot(); actor.setUserId("owner");
    when(authorization.currentActor()).thenReturn(actor);
    var mine = result("mine-result", "mine", "PASS", "80", 0, "80"); mine.setStableRank(1);
    var other = result("other-result", "other", "PASS", "70", 0, "70"); other.setStableRank(2);
    when(results.findResultsUnsorted("leader-run")).thenReturn(List.of(mine, other));
    ReflectionTestUtils.setField(manager, "persistence", runs);
    ReflectionTestUtils.setField(manager, "selectionItemPersistenceService", selections);
    ReflectionTestUtils.setField(manager, "weightedResultPersistenceService", results);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("match-run-test-signing-key-32-bytes"));
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());

    assertThat(manager.get("leader-run")).isSameAs(run);
    assertThat(manager.results("leader-run", null, 20).getItems())
        .extracting(value -> value.getCandidateId()).containsExactly("mine");
    when(runs.findOwnedPendingResultCandidateIds("leader-run", "owner")).thenReturn(List.of());
    doThrow(PlatformExceptions.forbidden("DENIED", "禁止访问"))
        .when(authorization).requireAccess(anyString(), any());
    assertThatThrownBy(() -> manager.get("leader-run")).isInstanceOf(PlatformException.class);
  }

  @Test
  void cursorIsBoundToOneRunAndRejectsTampering() {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    ReflectionTestUtils.setField(
        manager, "cursorCodec", new SignedCursorCodec("match-run-test-signing-key-32-bytes"));
    String cursor = manager.encodeCursor("run-a", 12);

    assertThat(manager.decodeCursor("run-a", cursor)).isEqualTo(12);
    assertThatThrownBy(() -> manager.decodeCursor("run-b", cursor))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MATCH_CURSOR_SCOPE_MISMATCH"));
    assertThatThrownBy(() -> manager.decodeCursor("run-a", "not-base64"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MATCH_CURSOR_SCOPE_MISMATCH"));
  }

  @Test
  void rankingUsesMustTierThenSelectedScoreThenCandidateIdAsStableTieBreaker() {
    List<WeightedResult> values =
        new ArrayList<>(
            List.of(
                result("unknown", "candidate-a", "UNKNOWN", "100", 20, "120"),
                result("pass-low", "candidate-z", "PASS", "80", 5, "85"),
                result("fail", "candidate-c", "FAIL", "100", 20, "120"),
                result("pass-tie-b", "candidate-b", "PASS", "90", 10, "100"),
                result("pass-tie-a", "candidate-a", "PASS", "90", 10, "100")));

    values.sort(MatchRunApplicationManager.resultComparator("REFERENCE_SCORE"));

    assertThat(values)
        .extracting(WeightedResult::getId)
        .containsExactly("pass-tie-a", "pass-tie-b", "pass-low", "unknown", "fail");
  }

  @Test
  void alternateSortStillKeepsMustTierDominant() {
    List<WeightedResult> values =
        new ArrayList<>(
            List.of(
                result("unknown-high", "candidate-a", "UNKNOWN", "99", 20, "119"),
                result("pass-low", "candidate-b", "PASS", "30", 5, "35")));

    values.sort(MatchRunApplicationManager.resultComparator("PROFESSIONAL_SCORE"));

    assertThat(values)
        .extracting(WeightedResult::getId)
        .containsExactly("pass-low", "unknown-high");
  }

  @Test
  void recordsUnexpectedDurableRunFailureWithCorrelationAndThrowable() {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    MatchRunAttemptPersistenceService attempts = mock(MatchRunAttemptPersistenceService.class);
    MatchRunItemOutcomePersistenceService outcomes = mock(MatchRunItemOutcomePersistenceService.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    when(tasks.renewLease(any())).thenReturn(true);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    JdFactQueryService jdFacts = mock(JdFactQueryService.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    MatchRun run = new MatchRun();
    run.setId("run-1");
    run.setJdFactSnapshotId("jd-facts-1");
    run.setTotalCount(3);
    IllegalStateException failure = new IllegalStateException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));
    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(2);
    when(task.status()).thenReturn("FAILED");
    when(persistence.findRunByTask("task-1")).thenReturn(run);
    when(ids.newId()).thenReturn("attempt-1");
    when(persistence.startRun(anyString(), any())).thenReturn(1);
    when(attempts.insertAttempt(anyString(), anyString(), anyInt(), any())).thenReturn(1);
    when(persistence.finishRun(anyString(), any(), anyInt(), anyInt(), any(), any()))
        .thenReturn(1);
    when(attempts.finishAttempt(anyString(), anyInt(), any(), any(), any())).thenReturn(1);
    when(tasks.fail(any(TaskSnapshot.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(task);
    when(outcomes.countSuccessfulOutcomes("run-1")).thenReturn(0);
    when(jdFacts.factsBySnapshotId("jd-facts-1")).thenThrow(failure);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchRunAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(manager, "matchRunItemOutcomePersistenceService", outcomes);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "transactionTemplate", transactionTemplate());
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
    Logger logger = (Logger) LoggerFactory.getLogger(MatchRunApplicationManager.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      manager.process(task);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getFormattedMessage()).contains("task-1", "run-1");
      assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
          .contains("java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
              "MatchRunApplicationManagerInvariantTest.java")
          .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    });
    verify(tasks).fail(task, "MATCH_RUN_FAILED", "匹配运行失败，可重试", true);
    assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ordinary-failure", "MATCH_AI_ASSESSMENT_BUSY", "MATCH_AI_ASSESSMENT_LEASE_LOST"})
  void matchingRunDoesNotLoadResumeFactsOneCandidateAtATime(String failureCode) {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    MatchRunAttemptPersistenceService attempts = mock(MatchRunAttemptPersistenceService.class);
    MatchRunItemOutcomePersistenceService outcomes = mock(MatchRunItemOutcomePersistenceService.class);
    MatchPlanVersionPersistenceService plans = mock(MatchPlanVersionPersistenceService.class);
    WeightProfileVersionPersistenceService weights =
        mock(WeightProfileVersionPersistenceService.class);
    ControlledCriterionPersistenceService criteria =
        mock(ControlledCriterionPersistenceService.class);
    ControlledCriterionPlanPersistenceService criterionPlans =
        mock(ControlledCriterionPlanPersistenceService.class);
    SelectionItemPersistenceService selections = mock(SelectionItemPersistenceService.class);
    WeightedResultPersistenceService results = mock(WeightedResultPersistenceService.class);
    AiMatchingEvaluationManager engine = mock(AiMatchingEvaluationManager.class);
    ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
    JdFactQueryService jdFacts = mock(JdFactQueryService.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    when(tasks.renewLease(any())).thenReturn(true);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    TaskSnapshot task = mock(TaskSnapshot.class);

    MatchRun run = new MatchRun();
    run.setId("run-1");
    run.setJdFactSnapshotId("jd-facts-1");
    run.setPlanVersionId("plan-1");
    run.setWeightProfileVersionId("weights-1");
    run.setCriterionPlanVersionId("criteria-1");
    run.setSelectionSnapshotId("selection-1");
    run.setSelectedCriterionIdsJson("[]");
    run.setSortMode("REFERENCE_SCORE");
    run.setTotalCount(2);
    List<SelectionItem> items =
        List.of(
            new SelectionItem("selection-1", 1, "candidate-1", "resume-1", "snapshot-1"),
            new SelectionItem("selection-1", 2, "candidate-2", "resume-2", "snapshot-2"));

    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(1);
    when(task.status()).thenReturn("FAILED");
    when(persistence.findRunByTask("task-1")).thenReturn(run);
    when(ids.newId()).thenReturn("attempt-1");
    when(persistence.startRun(anyString(), any())).thenReturn(1);
    when(attempts.insertAttempt(anyString(), anyString(), anyInt(), any())).thenReturn(1);
    when(persistence.upsertItemOutcome(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), any()))
        .thenReturn(1);
    when(persistence.finishRun(anyString(), any(), anyInt(), anyInt(), any(), any()))
        .thenReturn(1);
    when(attempts.finishAttempt(anyString(), anyInt(), any(), any(), any())).thenReturn(1);
    when(tasks.fail(any(TaskSnapshot.class), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(task);
    when(outcomes.countSuccessfulOutcomes("run-1")).thenReturn(0);
    when(outcomes.findSuccessfulResumeVersionIds("run-1")).thenReturn(List.of());
    when(jdFacts.factsBySnapshotId("jd-facts-1")).thenReturn(mock(JdFactSnapshotData.class));
    when(plans.findMatchPlanVersion("plan-1")).thenReturn(mock());
    WeightProfileVersion frozenWeights =
        new WeightProfileVersion("weights-1", "personal", 2, 47, 18, 21, 14, 0);
    when(weights.findWeightProfileVersion("weights-1")).thenReturn(frozenWeights);
    when(criteria.findCriteria("criteria-1")).thenReturn(List.of());
    when(criterionPlans.findCriterionPlan("criteria-1")).thenReturn(mock());
    when(selections.findSelectionItems("selection-1")).thenReturn(items);
    when(resumeFacts.snapshotFactBatch(any()))
        .thenReturn(id -> mock(ResumeFactSnapshotData.class));
    when(engine.evaluate(any(), any(), any(), any(), anyList(), anySet(), anyInt()))
        .thenThrow(failureCode.equals("ordinary-failure") ? new IllegalStateException("stop after fact lookup")
            : new PlatformException(HttpStatus.CONFLICT, failureCode, "等待冻结评分", true));
    TaskSnapshot queued = mock(TaskSnapshot.class);
    when(queued.status()).thenReturn("QUEUED");
    when(tasks.failAndRetryAt(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return queued;
    });
    when(results.findResultsUnsorted("run-1")).thenReturn(List.of());

    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchRunAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(manager, "matchRunItemOutcomePersistenceService", outcomes);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", weights);
    ReflectionTestUtils.setField(manager, "controlledCriterionPersistenceService", criteria);
    ReflectionTestUtils.setField(manager, "controlledCriterionPlanPersistenceService", criterionPlans);
    ReflectionTestUtils.setField(manager, "selectionItemPersistenceService", selections);
    ReflectionTestUtils.setField(manager, "weightedResultPersistenceService", results);
    ReflectionTestUtils.setField(manager, "engine", engine);
    ReflectionTestUtils.setField(manager, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "transactionTemplate", transactionTemplate());
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

    manager.process(task);

    verify(resumeFacts).snapshotFactBatch(List.of("snapshot-1", "snapshot-2"));
    verify(resumeFacts, never()).factsBySnapshotId(anyString());
    verify(engine, times(failureCode.equals("ordinary-failure") ? 2 : 1)).evaluate(
        any(), any(), any(), same(frozenWeights), anyList(), anySet(), anyInt());
    if (!failureCode.equals("ordinary-failure")) {
      verify(tasks).failAndRetryAt(eq(task), eq(failureCode), anyString(),
          eq(Instant.parse("2026-09-04T00:05:05Z")));
      verify(persistence).finishRun(eq("run-1"), eq(MatchRunStatus.QUEUED), eq(0), eq(0), eq(failureCode), any());
      verify(persistence, never()).upsertItemOutcome(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), any());
      verify(tasks, never()).fail(any(), anyString(), anyString(), any(Boolean.class));
    }
  }

  private static TransactionTemplate transactionTemplate() {
    return new TransactionTemplate(new TestTransactionManager());
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }

  @ParameterizedTest
  @ValueSource(strings = {"personal", "default", "zero", "negative"})
  void creatingMatchRunValidatesWeightsBeforeLoadingResumeFacts(String weightKind) {
    assertCreatingMatchRun(weightKind, "REFERENCE_SCORE", "REFERENCE_SCORE");
  }

  @ParameterizedTest
  @CsvSource(value = {
      "PROFESSIONAL_SCORE, PROFESSIONAL_SCORE",
      "professional_score, PROFESSIONAL_SCORE",
      "ProFessional_Score, PROFESSIONAL_SCORE",
      "' professional_score ', PROFESSIONAL_SCORE",
      "'\u2003professional_score\u2003', PROFESSIONAL_SCORE",
      "reference_score, REFERENCE_SCORE",
      "bonus_score, BONUS_SCORE",
      "NULL, BONUS_SCORE"
  }, nullValues = "NULL")
  @ResourceLock(Resources.LOCALE)
  void creatingMatchRunNormalizesSortIndependentlyOfDefaultLocale(String input, String expectedSort) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertCreatingMatchRun("personal", input, expectedSort);
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\u2003 \t", "unknown"})
  void creatingMatchRunRejectsBlankAndUnknownSortBeforeLookingUpIdempotency(String input) {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    AuthorizationService authorization = mock(AuthorizationService.class);
    MatchingIdempotencyPersistenceService idempotency = mock(MatchingIdempotencyPersistenceService.class);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot());
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "matchingIdempotencyPersistenceService", idempotency);
    CreateMatchRunDTO command = new CreateMatchRunDTO("jd-1", List.of("resume-1"), null, null, List.of(), input);

    assertThatThrownBy(() -> manager.create(command, "key-1"))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("MATCH_RUN_INVALID");
          assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        });
    verify(authorization).requirePermission("matching.run");
    verifyNoInteractions(idempotency);
  }

  private void assertCreatingMatchRun(String weightKind, String sortMode, String expectedSort) {
    assertCreatingMatchRun(weightKind, sortMode, expectedSort, false);
  }

  @ParameterizedTest
  @CsvSource(value = {"NULL, BONUS_SCORE", "REFERENCE_SCORE, REFERENCE_SCORE"}, nullValues = "NULL")
  void sameKeyDistinguishesDefaultSortFromAnExplicitOverrideInBothDirections(String input, String expectedSort) {
    assertCreatingMatchRun("personal", input, expectedSort, true);
  }

  @ParameterizedTest
  @CsvSource(value = {
      "NULL, BONUS_SCORE, true, REFERENCE_SCORE",
      "NULL, PROFESSIONAL_SCORE, true, REFERENCE_SCORE",
      "NULL, REFERENCE_SCORE, true, REFERENCE_SCORE",
      "' reference_score ', REFERENCE_SCORE, true, REFERENCE_SCORE",
      "REFERENCE_SCORE, BONUS_SCORE, false, REFERENCE_SCORE",
      "REFERENCE_SCORE, PROFESSIONAL_SCORE, false, REFERENCE_SCORE",
      "' bonus_score ', BONUS_SCORE, true, BONUS_SCORE",
      "professional_score, PROFESSIONAL_SCORE, true, PROFESSIONAL_SCORE",
      "NULL, BONUS_SCORE, false, BONUS_SCORE",
      "NULL, PROFESSIONAL_SCORE, false, PROFESSIONAL_SCORE"
  }, nullValues = "NULL")
  void legacyReplayPreservesFrozenSortAndRejectsAnExplicitContradiction(
      String input, String frozenSort, boolean accepted, String legacySort) {
    assertLegacySortReplay(input, frozenSort, accepted, legacySort, frozenSort);
  }

  @ParameterizedTest
  @CsvSource(value = {
      "NULL, BONUS_SCORE, false", "NULL, PROFESSIONAL_SCORE, false",
      "REFERENCE_SCORE, BONUS_SCORE, true", "REFERENCE_SCORE, PROFESSIONAL_SCORE, true",
      "NULL, MISSING, false", "NULL, MALFORMED, false", "NULL, INVALID, false",
      "NULL, ABSENT, false", "NULL, UNCONFIGURED, true", "NULL, JSON_NULL, false"
  }, nullValues = "NULL")
  void legacyReferenceReplayUsesTheExactFrozenWeightVersion(
      String input, String frozenDefault, boolean accepted) {
    assertLegacySortReplay(input, "REFERENCE_SCORE", accepted, "REFERENCE_SCORE", frozenDefault);
  }

  private void assertLegacySortReplay(String input, String frozenSort, boolean accepted,
      String legacySort, String frozenDefault) {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    AuthorizationService authorization = mock(AuthorizationService.class);
    MatchingIdempotencyPersistenceService idempotency = mock(MatchingIdempotencyPersistenceService.class);
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    MatchPlanVersionPersistenceService plans = mock(MatchPlanVersionPersistenceService.class);
    WeightProfileVersionPersistenceService weights = mock(WeightProfileVersionPersistenceService.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    String legacyFingerprint = switch (legacySort) {
      case "BONUS_SCORE" -> LEGACY_BONUS_FINGERPRINT;
      case "PROFESSIONAL_SCORE" -> LEGACY_PROFESSIONAL_FINGERPRINT;
      default -> LEGACY_REFERENCE_FINGERPRINT;
    };
    IdempotencyRecord record = new IdempotencyRecord("actor-1", "idempotency-1", "CREATE_MATCH_RUN",
        legacyFingerprint, "run-1");
    MatchRun run = new MatchRun();
    run.setId("run-1");
    run.setSortMode(frozenSort);
    run.setWeightProfileVersionId("weights-1");
    WeightProfileVersion frozenWeights = new WeightProfileVersion("weights-1", "profile-1", 1, 20, 20, 20, 20, 20);
    frozenWeights.setScreeningSettingsJson(switch (frozenDefault) {
      case "MALFORMED" -> "{";
      case "INVALID" -> "{\"criteria\":[],\"defaultSortMode\":\"UNKNOWN\"}";
      case "ABSENT" -> "{\"criteria\":[]}";
      case "JSON_NULL" -> "null";
      case "UNCONFIGURED" -> null;
      default -> "{\"criteria\":[],\"defaultSortMode\":\"" + frozenDefault + "\"}";
    });
    when(authorization.currentActor()).thenReturn(actor);
    when(idempotency.findIdempotency("actor-1", "idempotency-1")).thenReturn(record);
    when(persistence.findRun("run-1")).thenReturn(run);
    when(weights.findWeightProfileVersion("weights-1"))
        .thenReturn("MISSING".equals(frozenDefault) ? null : frozenWeights);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "matchingIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", weights);
    CreateMatchRunDTO command = new CreateMatchRunDTO("jd-version-1", List.of("resume-1", "resume-2"),
        "weights-1", null, List.of(), input);

    if (accepted) {
      assertThat(manager.create(command, "idempotency-1")).isSameAs(run);
      command.setJdVersionId("another-jd");
      assertThatThrownBy(() -> manager.create(command, "idempotency-1"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
      command.setJdVersionId("jd-version-1");
      record.setActionCode("ANOTHER_ACTION");
      assertThatThrownBy(() -> manager.create(command, "idempotency-1"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    } else {
      assertThatThrownBy(() -> manager.create(command, "idempotency-1"))
          .isInstanceOfSatisfying(PlatformException.class, error -> {
            assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          });
    }
    verify(authorization, times(accepted ? 3 : 1)).requirePermission("matching.run");
    verifyNoInteractions(plans);
    if (input == null && "REFERENCE_SCORE".equals(legacySort) && "REFERENCE_SCORE".equals(frozenSort)) {
      verify(weights).findWeightProfileVersion("weights-1");
    } else {
      verifyNoInteractions(weights);
    }
  }

  private void assertCreatingMatchRun(String weightKind, String sortMode, String expectedSort, boolean verifyReplay) {
    boolean useDefaultWeights = "default".equals(weightKind);
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    AuthorizationService authorization = mock(AuthorizationService.class);
    MatchingIdempotencyPersistenceService idempotency =
        mock(MatchingIdempotencyPersistenceService.class);
    MatchPlanVersionPersistenceService plans = mock(MatchPlanVersionPersistenceService.class);
    PersonalPreferencePersistenceService preferences =
        mock(PersonalPreferencePersistenceService.class);
    WeightProfileVersionPersistenceService weightVersions =
        mock(WeightProfileVersionPersistenceService.class);
    WeightProfilePersistenceService weightProfiles = mock(WeightProfilePersistenceService.class);
    ControlledCriterionPersistenceService criteria =
        mock(ControlledCriterionPersistenceService.class);
    ControlledCriterionPlanPersistenceService criterionPlans =
        mock(ControlledCriterionPlanPersistenceService.class);
    CandidateSelectionSnapshotPersistenceService selections =
        mock(CandidateSelectionSnapshotPersistenceService.class);
    SelectionItemPersistenceService selectionItems = mock(SelectionItemPersistenceService.class);
    ScreeningAdmissionPersistenceService admissions = mock(ScreeningAdmissionPersistenceService.class);
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
    JdFactQueryService jdFacts = mock(JdFactQueryService.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    when(tasks.renewLease(any())).thenReturn(true);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    actor.setHomeDepartmentId("department-1");
    actor.setManagedUserIds(Set.of());
    actor.setExplicitlyGrantedDepartmentIds(Set.of());
    MatchPlanVersion plan = new MatchPlanVersion();
    plan.setId("plan-1");
    plan.setControlledCriterionPlanVersionId("criteria-1");
    WeightProfileVersion weights = new WeightProfileVersion("weights-1", "profile-1", 1, 20, 20, 20, 20, 20);
    if (sortMode == null || verifyReplay) {
      weights.setScreeningSettingsJson("{\"criteria\":[],\"defaultSortMode\":\"BONUS_SCORE\"}");
    }
    if ("zero".equals(weightKind) || "negative".equals(weightKind)) {
      weights.setYearsWeight("zero".equals(weightKind) ? 0 : -1);
      weights.setSkillsWeight("zero".equals(weightKind) ? 40 : 41);
    }
    String weightId = useDefaultWeights ? DefaultScoringConstants.WEIGHT_VERSION_ID : "weights-1";
    weights.setId(weightId);
    weights.setProfileId("profile-1");
    WeightProfile profile = new WeightProfile();
    profile.setOwnerUserId("actor-1");
    ControlledCriterionPlan criterionPlan = new ControlledCriterionPlan();
    criterionPlan.setId("criteria-1");
    JdFactSnapshotData jd = mock(JdFactSnapshotData.class);
    ResumeFactSnapshotData resume1 = resume("snapshot-1", "resume-1", "candidate-1");
    ResumeFactSnapshotData resume2 = resume("snapshot-2", "resume-2", "candidate-2");
    MatchRun storedRun = new MatchRun();
    storedRun.setId("run-1");
    storedRun.setSortMode(expectedSort);
    CreateMatchRunDTO command =
        new CreateMatchRunDTO(
            "jd-version-1",
            List.of("resume-1", "resume-2"),
            useDefaultWeights ? null : weightId,
            null,
            List.of(),
            sortMode);

    when(authorization.currentActor()).thenReturn(actor);
    when(plans.findActiveMatchPlanVersion("jd-version-1")).thenReturn(plan);
    when(jdFacts.currentFacts("jd-version-1")).thenReturn(jd);
    when(jd.getSnapshotId()).thenReturn("jd-snapshot-1");
    when(weightVersions.findWeightProfileVersion(weightId)).thenReturn(weights);
    when(weightProfiles.findWeightProfile("profile-1")).thenReturn(profile);
    when(criteria.findCriteria("criteria-1")).thenReturn(List.of());
    when(criterionPlans.findCriterionPlan("criteria-1")).thenReturn(criterionPlan);
    when(resumeFacts.currentFactBatch(List.of("resume-1", "resume-2")))
        .thenReturn(id -> "resume-1".equals(id) ? resume1 : resume2);
    when(admissions.findScreeningCandidates(
        List.of("candidate-1", "candidate-2"), "actor-1", false, Set.of(), Set.of(), false))
        .thenReturn(List.of(
            admitted("candidate-1", "resume-1", "jd-version-1"),
            admitted("candidate-2", "resume-2", "jd-version-1")));
    when(selectionItems.insertSelectionItems(anyList())).thenReturn(true);
    when(ids.newId()).thenReturn("selection-1", "run-1", "task-1");
    when(persistence.findRun("run-1")).thenReturn(storedRun);

    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "matchingIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "personalPreferencePersistenceService", preferences);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", weightVersions);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", weightProfiles);
    ReflectionTestUtils.setField(manager, "controlledCriterionPersistenceService", criteria);
    ReflectionTestUtils.setField(manager, "controlledCriterionPlanPersistenceService", criterionPlans);
    ReflectionTestUtils.setField(
        manager, "candidateSelectionSnapshotPersistenceService", selections);
    ReflectionTestUtils.setField(manager, "selectionItemPersistenceService", selectionItems);
    ReflectionTestUtils.setField(manager, "screeningAdmissionPersistenceService", admissions);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

    if ("negative".equals(weightKind)) {
      assertThatThrownBy(() -> manager.create(command, "idempotency-1"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("WEIGHT_PROFILE_INVALID"));
      verifyNoInteractions(resumeFacts, selections, selectionItems, persistence);
      return;
    }
    assertThatCode(() -> assertThat(manager.create(command, "idempotency-1")).isSameAs(storedRun))
        .doesNotThrowAnyException();

    verify(weightVersions).findWeightProfileVersion(weightId);
    verify(persistence).insertRun(
        eq("run-1"), eq("task-1"), eq("jd-version-1"), eq("jd-snapshot-1"),
        eq("plan-1"), eq("criteria-1"), eq(weightId), isNull(), eq("CUSTOM"),
        eq("selection-1"), eq("[]"), eq(expectedSort), eq(2),
        eq("actor-1"), eq("department-1"), any());
    verify(resumeFacts).currentFactBatch(List.of("resume-1", "resume-2"));
    verify(resumeFacts, never()).currentFacts(anyString());
    verify(admissions).findScreeningCandidates(
        List.of("candidate-1", "candidate-2"), "actor-1", false, Set.of(), Set.of(), false);
    verify(selectionItems)
        .insertSelectionItems(
            ArgumentMatchers.argThat(
                items ->
                    items.size() == 2
                        && items.get(0).getSequenceNo() == 1
                        && items.get(1).getSequenceNo() == 2));
    if (verifyReplay) {
      ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
      verify(idempotency).insertIdempotency(eq("actor-1"), eq("idempotency-1"), eq("CREATE_MATCH_RUN"),
          fingerprint.capture(), eq("run-1"), any());
      assertThat(fingerprint.getValue()).matches("[0-9a-f]{64}");
      IdempotencyRecord record = new IdempotencyRecord("actor-1", "idempotency-1", "CREATE_MATCH_RUN",
          fingerprint.getValue(), "run-1");
      when(idempotency.findIdempotency("actor-1", "idempotency-1")).thenReturn(record);
      // 重放必须使用已冻结结果，不受当前默认配置变化影响。
      weights.setScreeningSettingsJson("{\"criteria\":[],\"defaultSortMode\":\"PROFESSIONAL_SCORE\"}");
      assertThat(manager.create(command, "idempotency-1")).isSameAs(storedRun);
      if (sortMode != null) {
        command.setSortMode(" reference_score ");
        assertThat(manager.create(command, "idempotency-1")).isSameAs(storedRun);
      }
      command.setSortMode(sortMode == null ? "REFERENCE_SCORE" : null);
      assertThatThrownBy(() -> manager.create(command, "idempotency-1"))
          .isInstanceOfSatisfying(PlatformException.class, error -> {
            assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          });
      verify(weightVersions, times(1)).findWeightProfileVersion(weightId);
      verify(selectionItems, times(1)).insertSelectionItems(anyList());
      verify(tasks, times(1)).enqueue(ArgumentMatchers.argThat(
          task -> fingerprint.getValue().equals(task.requestFingerprint())));

      String nextSort = sortMode == null ? "REFERENCE_SCORE" : "PROFESSIONAL_SCORE";
      MatchRun nextRun = new MatchRun();
      nextRun.setId("run-2");
      nextRun.setSortMode(nextSort);
      when(ids.newId()).thenReturn("selection-2", "run-2", "task-2");
      when(persistence.findRun("run-2")).thenReturn(nextRun);
      assertThat(manager.create(command, "idempotency-2")).isSameAs(nextRun);
      verify(persistence).insertRun(
          eq("run-2"), eq("task-2"), eq("jd-version-1"), eq("jd-snapshot-1"),
          eq("plan-1"), eq("criteria-1"), eq(weightId), isNull(), eq("CUSTOM"),
          eq("selection-2"), eq("[]"), eq(nextSort), eq(2),
          eq("actor-1"), eq("department-1"), any());
      ArgumentCaptor<EnqueueTask> queued = ArgumentCaptor.forClass(EnqueueTask.class);
      verify(tasks, times(2)).enqueue(queued.capture());
      assertThat(queued.getAllValues().get(1).requestFingerprint()).isNotEqualTo(fingerprint.getValue());
    }
  }

  @Test
  void rankingPersistsAllStableRanksWithOneBatchUpdate() {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    WeightedResultPersistenceService results = mock(WeightedResultPersistenceService.class);
    MatchRun run = new MatchRun();
    run.setId("run-1");
    run.setSortMode("REFERENCE_SCORE");
    List<WeightedResult> unsorted =
        List.of(
            result("lower", "candidate-b", "PASS", "80", 0, "80"),
            result("higher", "candidate-a", "PASS", "90", 0, "90"));
    when(results.findResultsUnsorted("run-1")).thenReturn(unsorted);
    when(results.updateRanks(anyString(), anyList(), any())).thenReturn(2);
    ReflectionTestUtils.setField(manager, "weightedResultPersistenceService", results);
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

    ReflectionTestUtils.invokeMethod(manager, "rank", run);

    verify(results)
        .updateRanks(
            ArgumentMatchers.eq("run-1"),
            ArgumentMatchers.argThat(
                ranked ->
                    ranked.size() == 2
                        && "higher".equals(ranked.get(0).getId())
                        && ranked.get(0).getStableRank() == 1
                        && "lower".equals(ranked.get(1).getId())
                        && ranked.get(1).getStableRank() == 2),
            any());
  }

  private static WeightedResult result(
      String id,
      String candidateId,
      String tier,
      String professional,
      int bonus,
      String reference) {
    return new WeightedResult(
        id,
        "run",
        "base-" + id,
        candidateId,
        "resume-" + id,
        new BigDecimal(professional),
        bonus,
        new BigDecimal(reference),
        tier,
        null,
        "{}");
  }

  private static ResumeFactSnapshotData resume(
      String snapshotId, String resumeVersionId, String candidateId) {
    ResumeFactSnapshotData result = new ResumeFactSnapshotData();
    result.setSnapshotId(snapshotId);
    result.setResumeVersionId(resumeVersionId);
    result.setCandidateId(candidateId);
    return result;
  }

  @Test
  void customRunRejectsAResumeWhoseExactJdTripleWasNotAdmitted() {
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    ScreeningAdmissionPersistenceService admissions = mock(ScreeningAdmissionPersistenceService.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    actor.setManagedUserIds(Set.of());
    actor.setExplicitlyGrantedDepartmentIds(Set.of());
    ResumeFactSnapshotData resume = resume("snapshot-1", "resume-1", "candidate-1");
    when(admissions.findScreeningCandidates(
        List.of("candidate-1"), "actor-1", false, Set.of(), Set.of(), false))
        .thenReturn(List.of(admitted("candidate-1", "resume-1", "another-jd")));
    ReflectionTestUtils.setField(manager, "screeningAdmissionPersistenceService", admissions);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
        manager, "requireScreeningAdmissions", "jd-version-1", List.of(resume), actor))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_ADMISSION_REQUIRED"));
  }

  private static ScreeningCandidateRow admitted(
      String candidateId, String resumeVersionId, String jdVersionId) {
    ScreeningCandidateRow row = new ScreeningCandidateRow();
    row.setCandidateId(candidateId);
    row.setResumeVersionId(resumeVersionId);
    row.setJdVersionId(jdVersionId);
    row.setAdmissionId("admission-" + candidateId);
    return row;
  }
}
