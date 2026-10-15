package com.ezaigc.hr.intelligence.matching.application.manager;

import com.ezaigc.hr.talent.api.CandidateDirectoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchCriterionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchDimensionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchEvaluationDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.BaseEvaluation;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunAttemptStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.service.BaseEvaluationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.CriterionEvaluationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunItemOutcomePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingHighlightPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;
import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentMatchers;

class MatchRunApplicationManagerTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void intakeScoringPreparationAllowsTheJdLockInsideItsTransaction() {
    var manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    var authorization = mock(AuthorizationService.class);
    var jdFacts = mock(JdFactQueryService.class);
    var plans = mock(MatchPlanVersionPersistenceService.class);
    var jd = mock(JdFactSnapshotData.class);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    when(jdFacts.currentFacts("jd-version")).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
      return jd;
    });
    when(plans.findActiveMatchPlanVersion("jd-version")).thenReturn(new MatchPlanVersion());
    var proxy = new ProxyFactory(manager);
    proxy.setProxyTargetClass(true);
    var interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(new RecordingTransactionManager());
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);

    assertThat(((MatchRunApplicationManager) proxy.getProxy())
        .requireIntakeScoringReady("jd-version")).isSameAs(jd);
    verify(authorization).requirePermission("matching.run");
    verify(plans).findActiveMatchPlanVersion("jd-version");
  }

  @Test
  void lostLeaseCannotStartARunOrClosePreviousAttempts() {
    var transactions = new RecordingTransactionManager();
    var manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    var persistence = mock(MatchRunPersistenceService.class);
    var attempts = mock(MatchRunAttemptPersistenceService.class);
    var tasks = mock(AsyncTaskPort.class);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchRunAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    var task = mock(TaskSnapshot.class);
    when(tasks.renewLease(task)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return false;
    });
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(manager, "startAttempt", run(), task, NOW))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(persistence, attempts);
    assertThat(transactions.rolledBack()).isOne();
  }

  @Test
  void cancellationAlreadyCompletedByRecoveryDoesNotWriteAgain() {
    var transactions = new RecordingTransactionManager();
    var manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    var persistence = mock(MatchRunPersistenceService.class);
    var attempts = mock(MatchRunAttemptPersistenceService.class);
    var tasks = mock(AsyncTaskPort.class);
    var task = mock(TaskSnapshot.class);
    var run = run();
    run.setStatus("CANCELLED");
    when(task.id()).thenReturn("task-1");
    when(tasks.cancelled(task)).thenReturn(task);
    when(persistence.findBoundRunForUpdate("run-1", "task-1")).thenReturn(run);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchRunAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    ReflectionTestUtils.invokeMethod(manager, "finishCancelledRunAndTask", task, run, 1, 0, NOW);
    verify(persistence, never()).finishRun(any(), any(), anyInt(), anyInt(), any(), any());
    verifyNoInteractions(attempts);
    assertThat(transactions.committed()).isOne();
  }

  @Test
  void persistsRunLifecycleTransitionsInAtomicTransactions() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    MatchRunAttemptPersistenceService attempts = mock(MatchRunAttemptPersistenceService.class);
    MatchRunItemOutcomePersistenceService outcomes = mock(MatchRunItemOutcomePersistenceService.class);
    MatchPlanVersionPersistenceService plans = mock(MatchPlanVersionPersistenceService.class);
    WeightProfileVersionPersistenceService weights = mock(WeightProfileVersionPersistenceService.class);
    ControlledCriterionPersistenceService criteria = mock(ControlledCriterionPersistenceService.class);
    ControlledCriterionPlanPersistenceService criterionPlans =
        mock(ControlledCriterionPlanPersistenceService.class);
    SelectionItemPersistenceService selections = mock(SelectionItemPersistenceService.class);
    WeightedResultPersistenceService results = mock(WeightedResultPersistenceService.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    when(tasks.renewLease(any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
    JdFactQueryService jdFacts = mock(JdFactQueryService.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    MatchRun run = run();
    run.setTotalCount(0);
    ControlledCriterionPlan criterionPlan = new ControlledCriterionPlan();
    AtomicBoolean runStartInsideTransaction = new AtomicBoolean();
    AtomicBoolean attemptStartInsideTransaction = new AtomicBoolean();
    AtomicBoolean runFinishInsideTransaction = new AtomicBoolean();
    AtomicBoolean attemptFinishInsideTransaction = new AtomicBoolean();
    AtomicBoolean taskFinishInsideTransaction = new AtomicBoolean();

    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(1);
    when(persistence.findRunByTask("task-1")).thenReturn(run);
    when(persistence.startRun(eq("run-1"), eq(NOW)))
        .thenAnswer(
            invocation -> {
              runStartInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(attempts.insertAttempt(eq("generated-id"), eq("run-1"), eq(1), eq(NOW)))
        .thenAnswer(
            invocation -> {
              attemptStartInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(outcomes.countSuccessfulOutcomes("run-1")).thenReturn(0);
    when(outcomes.findSuccessfulResumeVersionIds("run-1")).thenReturn(List.of());
    when(jdFacts.factsBySnapshotId("jd-facts-1")).thenReturn(mock());
    when(plans.findMatchPlanVersion("plan-1")).thenReturn(mock());
    when(weights.findWeightProfileVersion("weights-1")).thenReturn(mock());
    when(criteria.findCriteria("criteria-1")).thenReturn(List.of());
    when(criterionPlans.findCriterionPlan("criteria-1")).thenReturn(criterionPlan);
    when(selections.findSelectionItems("selection-1")).thenReturn(List.of());
    when(resumeFacts.snapshotFactBatch(List.of())).thenReturn(id -> null);
    when(results.findResultsUnsorted("run-1")).thenReturn(List.of());
    when(persistence.finishRun(
            eq("run-1"), eq(MatchRunStatus.SUCCEEDED), eq(0), eq(0), isNull(), eq(NOW)))
        .thenAnswer(
            invocation -> {
              runFinishInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(attempts.finishAttempt(
            eq("run-1"), eq(1), eq(MatchRunAttemptStatus.SUCCEEDED), isNull(), eq(NOW)))
        .thenAnswer(
            invocation -> {
              attemptFinishInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(tasks.succeed(task, "run-1"))
        .thenAnswer(
            invocation -> {
              taskFinishInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return task;
            });

    inject(
        manager,
        persistence,
        attempts,
        outcomes,
        plans,
        weights,
        criteria,
        criterionPlans,
        selections,
        results,
        mock(BaseEvaluationPersistenceService.class),
        mock(MatchingHighlightPersistenceService.class),
        mock(CriterionEvaluationPersistenceService.class),
        mock(AiMatchingEvaluationManager.class),
        resumeFacts,
        jdFacts,
        tasks,
        transactions);

    manager.process(task);

    assertThat(runStartInsideTransaction).isTrue();
    assertThat(attemptStartInsideTransaction).isTrue();
    assertThat(runFinishInsideTransaction).isTrue();
    assertThat(attemptFinishInsideTransaction).isTrue();
    assertThat(taskFinishInsideTransaction).isTrue();
    assertThat(transactions.begun()).isEqualTo(3);
    assertThat(transactions.committed()).isEqualTo(3);
    assertThat(transactions.rolledBack()).isZero();
  }

  @Test
  void commitsCancellationWhenItWinsTheFinalSuccessRace() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    MatchRunAttemptPersistenceService attempts = mock(MatchRunAttemptPersistenceService.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    when(tasks.renewLease(any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    TaskSnapshot task = mock(TaskSnapshot.class);
    MatchRun run = run();

    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(1);
    when(tasks.succeed(task, "run-1"))
        .thenThrow(
            PlatformExceptions.conflict(
                "TASK_STATE_CONFLICT", "任务状态已变化，请刷新后重试"));
    when(tasks.shouldStopProcessing(task)).thenReturn(true);
    when(tasks.cancelled(task)).thenReturn(task);
    when(persistence.finishRun(
            "run-1", MatchRunStatus.CANCELLED, 1, 0, null, NOW))
        .thenReturn(1);
    when(attempts.finishAttempt(
            "run-1", 1, MatchRunAttemptStatus.CANCELLED, null, NOW))
        .thenReturn(1);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchRunAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactions));

    ReflectionTestUtils.invokeMethod(
        manager,
        "finishRunAndTask",
        task,
        run,
        MatchRunStatus.SUCCEEDED,
        1,
        0,
        MatchRunAttemptStatus.SUCCEEDED,
        null,
        null,
        true,
        NOW);

    verify(persistence)
        .finishRun("run-1", MatchRunStatus.CANCELLED, 1, 0, null, NOW);
    verify(persistence, never())
        .finishRun("run-1", MatchRunStatus.SUCCEEDED, 1, 0, null, NOW);
    assertThat(transactions.begun()).isEqualTo(2);
    assertThat(transactions.committed()).isEqualTo(1);
    assertThat(transactions.rolledBack()).isEqualTo(1);
  }

  @Test
  void rollsBackFailedCandidateBeforeRecordingFailureInIndependentTransaction() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    MatchRunApplicationManager manager = new MatchRunApplicationManager();
    ReflectionTestUtils.setField(manager, "candidateDirectory", mock(CandidateDirectoryService.class));
    MatchRunPersistenceService persistence = mock(MatchRunPersistenceService.class);
    MatchRunAttemptPersistenceService attempts = mock(MatchRunAttemptPersistenceService.class);
    MatchRunItemOutcomePersistenceService outcomes = mock(MatchRunItemOutcomePersistenceService.class);
    MatchPlanVersionPersistenceService plans = mock(MatchPlanVersionPersistenceService.class);
    WeightProfileVersionPersistenceService weights = mock(WeightProfileVersionPersistenceService.class);
    ControlledCriterionPersistenceService criteria = mock(ControlledCriterionPersistenceService.class);
    ControlledCriterionPlanPersistenceService criterionPlans =
        mock(ControlledCriterionPlanPersistenceService.class);
    SelectionItemPersistenceService selections = mock(SelectionItemPersistenceService.class);
    WeightedResultPersistenceService results = mock(WeightedResultPersistenceService.class);
    BaseEvaluationPersistenceService baseEvaluations = mock(BaseEvaluationPersistenceService.class);
    MatchingHighlightPersistenceService highlights = mock(MatchingHighlightPersistenceService.class);
    CriterionEvaluationPersistenceService criterionEvaluations =
        mock(CriterionEvaluationPersistenceService.class);
    AiMatchingEvaluationManager engine = mock(AiMatchingEvaluationManager.class);
    ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
    JdFactQueryService jdFacts = mock(JdFactQueryService.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    when(tasks.renewLease(any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    TaskSnapshot task = mock(TaskSnapshot.class);
    AtomicBoolean baseInsertInsideTransaction = new AtomicBoolean();
    AtomicBoolean failureOutcomeInsideTransaction = new AtomicBoolean();
    AtomicBoolean evaluationOutsideTransaction = new AtomicBoolean();

    MatchRun run = run();
    SelectionItem item = new SelectionItem("selection-1", 1, "candidate-1", "resume-1", "snapshot-1");
    JdFactSnapshotData jd = mock(JdFactSnapshotData.class);
    ResumeFactSnapshotData resume = mock(ResumeFactSnapshotData.class);
    ControlledCriterionPlan criterionPlan = new ControlledCriterionPlan();
    BaseEvaluation storedBase = new BaseEvaluation();
    storedBase.setId("base-1");

    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(1);
    when(persistence.findRunByTask("task-1")).thenReturn(run);
    when(persistence.startRun("run-1", NOW)).thenReturn(1);
    when(attempts.insertAttempt("generated-id", "run-1", 1, NOW)).thenReturn(1);
    when(outcomes.countSuccessfulOutcomes("run-1")).thenReturn(0);
    when(outcomes.findSuccessfulResumeVersionIds("run-1")).thenReturn(List.of());
    when(jdFacts.factsBySnapshotId("jd-facts-1")).thenReturn(jd);
    when(plans.findMatchPlanVersion("plan-1")).thenReturn(mock());
    when(weights.findWeightProfileVersion("weights-1")).thenReturn(mock());
    when(criteria.findCriteria("criteria-1")).thenReturn(List.of());
    when(criterionPlans.findCriterionPlan("criteria-1")).thenReturn(criterionPlan);
    when(selections.findSelectionItems("selection-1")).thenReturn(List.of(item));
    when(resumeFacts.snapshotFactBatch(List.of("snapshot-1"))).thenReturn(id -> resume);
    when(resume.getCandidateId()).thenReturn("candidate-1");
    when(resume.getResumeVersionId()).thenReturn("resume-1");
    when(engine.evaluate(any(), any(), any(), any(), anyList(), anySet(), anyInt()))
        .thenAnswer(
            invocation -> {
              evaluationOutsideTransaction.set(
                  !TransactionSynchronizationManager.isActualTransactionActive());
              return evaluation();
            });
    AtomicInteger guardedItemWrites = new AtomicInteger();
    when(tasks.updateProgress(any(), anyInt())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      guardedItemWrites.incrementAndGet();
      return task;
    });
    when(baseEvaluations.findBaseByCache(any())).thenReturn(null, storedBase);
    when(persistence.insertBase(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(),
            any(), anyInt(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              baseInsertInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(highlights.insertHighlightBatch(anyList())).thenReturn(true);
    IllegalStateException failure = new IllegalStateException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));
    when(criterionEvaluations.insertCriterionEvaluationBatch(anyList())).thenThrow(failure);
    when(persistence.upsertItemOutcome(
            eq("run-1"),
            eq("candidate-1"),
            eq("resume-1"),
            eq(MatchRunItemOutcomeStatus.FAILED.getCode()),
            eq("MATCH_ITEM_FAILED"),
            eq(1),
            any()))
        .thenAnswer(
            invocation -> {
              failureOutcomeInsideTransaction.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(results.findResultsUnsorted("run-1")).thenReturn(List.of());
    when(persistence.finishRun(
            eq("run-1"),
            eq(MatchRunStatus.FAILED),
            eq(0),
            eq(1),
            eq("MATCH_ITEMS_FAILED"),
            eq(NOW)))
        .thenReturn(1);
    when(attempts.finishAttempt(
            "run-1", 1, MatchRunAttemptStatus.FAILED, "MATCH_ITEMS_FAILED", NOW))
        .thenReturn(1);
    when(task.status()).thenReturn(MatchRunStatus.FAILED.getCode());
    when(tasks.fail(
            task, "MATCH_ITEMS_FAILED", "候选人匹配均失败，可重试", true))
        .thenReturn(task);

    inject(
        manager,
        persistence,
        attempts,
        outcomes,
        plans,
        weights,
        criteria,
        criterionPlans,
        selections,
        results,
        baseEvaluations,
        highlights,
        criterionEvaluations,
        engine,
        resumeFacts,
        jdFacts,
        tasks,
        transactions);

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
      assertThat(event.getFormattedMessage()).contains("task-1", "run-1", "candidate-1", "resume-1");
      assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
          .contains("java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
              "MatchRunApplicationManagerTransactionTest.java")
          .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    });
    assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
    assertThat(baseInsertInsideTransaction).isTrue();
    assertThat(failureOutcomeInsideTransaction).isTrue();
    assertThat(guardedItemWrites).hasValue(2);
    assertThat(evaluationOutsideTransaction).isTrue();
    assertThat(transactions.begun()).isEqualTo(5);
    assertThat(transactions.committed()).isEqualTo(4);
    assertThat(transactions.rolledBack()).isEqualTo(1);
    verify(highlights)
        .insertHighlightBatch(
            ArgumentMatchers.argThat(
                values ->
                    values.size() == 1
                        && "CORE_SKILLS".equals(values.getFirst().getDimensionCode())
                        && values.getFirst().getSequenceNo() == 1));
    verify(criterionEvaluations)
        .insertCriterionEvaluationBatch(
            ArgumentMatchers.argThat(
                values ->
                    values.size() == 1
                        && "AGE".equals(values.getFirst().getCriterionCode())
                        && "[\"30\"]".equals(values.getFirst().getActualJson())));
    verify(persistence, never())
        .upsertItemOutcome(
            eq("run-1"),
            eq("candidate-1"),
            eq("resume-1"),
            eq(MatchRunItemOutcomeStatus.SUCCEEDED.getCode()),
            isNull(),
            eq(1),
            any());
  }

  private static MatchRun run() {
    MatchRun run = new MatchRun();
    run.setId("run-1");
    run.setJdFactSnapshotId("jd-facts-1");
    run.setPlanVersionId("plan-1");
    run.setWeightProfileVersionId("weights-1");
    run.setCriterionPlanVersionId("criteria-1");
    run.setSelectionSnapshotId("selection-1");
    run.setSelectedCriterionIdsJson("[]");
    run.setSortMode("REFERENCE_SCORE");
    run.setTotalCount(1);
    return run;
  }

  private static MatchEvaluationDTO evaluation() {
    return new MatchEvaluationDTO(
        "jd-facts-1",
        "snapshot-1",
        "candidate-1",
        "resume-1",
        LocalDate.of(2026, 9, 5),
        BigDecimal.valueOf(80),
        0,
        BigDecimal.valueOf(80),
        "PASS",
        0,
        List.of(
            new MatchDimensionResultDTO(
                "CORE_SKILLS", true, 100, "KNOWN", BigDecimal.valueOf(80), "命中", List.of("Java ← Java"))),
        List.of(
            new MatchCriterionResultDTO(
                "definition-1",
                "AGE",
                "MUST",
                "PASS",
                true,
                5,
                5,
                "年龄满足",
                List.of("30"),
                List.of("18", "45"))));
  }

  private static void inject(
      MatchRunApplicationManager manager,
      MatchRunPersistenceService persistence,
      MatchRunAttemptPersistenceService attempts,
      MatchRunItemOutcomePersistenceService outcomes,
      MatchPlanVersionPersistenceService plans,
      WeightProfileVersionPersistenceService weights,
      ControlledCriterionPersistenceService criteria,
      ControlledCriterionPlanPersistenceService criterionPlans,
      SelectionItemPersistenceService selections,
      WeightedResultPersistenceService results,
      BaseEvaluationPersistenceService baseEvaluations,
      MatchingHighlightPersistenceService highlights,
      CriterionEvaluationPersistenceService criterionEvaluations,
      AiMatchingEvaluationManager engine,
      ResumeFactQueryService resumeFacts,
      JdFactQueryService jdFacts,
      AsyncTaskPort tasks,
      RecordingTransactionManager transactions) {
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchRunAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(manager, "matchRunItemOutcomePersistenceService", outcomes);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", weights);
    ReflectionTestUtils.setField(manager, "controlledCriterionPersistenceService", criteria);
    ReflectionTestUtils.setField(manager, "controlledCriterionPlanPersistenceService", criterionPlans);
    ReflectionTestUtils.setField(manager, "selectionItemPersistenceService", selections);
    ReflectionTestUtils.setField(manager, "weightedResultPersistenceService", results);
    ReflectionTestUtils.setField(manager, "baseEvaluationPersistenceService", baseEvaluations);
    ReflectionTestUtils.setField(manager, "matchingHighlightPersistenceService", highlights);
    ReflectionTestUtils.setField(
        manager, "criterionEvaluationPersistenceService", criterionEvaluations);
    ReflectionTestUtils.setField(manager, "engine", engine);
    ReflectionTestUtils.setField(manager, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> "generated-id");
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    if (ReflectionUtils.findField(MatchRunApplicationManager.class, "transactionTemplate") != null) {
      ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    }
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final AtomicInteger begun = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();
    private final AtomicInteger rolledBack = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun.incrementAndGet();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      committed.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rolledBack.incrementAndGet();
    }

    int begun() {
      return begun.get();
    }

    int committed() {
      return committed.get();
    }

    int rolledBack() {
      return rolledBack.get();
    }
  }
}
