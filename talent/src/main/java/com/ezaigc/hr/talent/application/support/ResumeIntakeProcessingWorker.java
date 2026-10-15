package com.ezaigc.hr.talent.application.support;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.AI;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.talent.application.manager.ResumeIntakeParsingManager;
import com.ezaigc.hr.talent.constant.ResumeIntakeWorkerConstants;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.enums.ResumeExtractionErrorCode;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator.ContractViolation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在有界 AI 线程池中处理共享文件，定时线程只提交触发和续租。 */
@Component
@ConditionalOnProperty(name = "hr.resume-intake.worker-enabled", havingValue = "true", matchIfMissing = true)
@ManagedScheduledWorker
public class ResumeIntakeProcessingWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  private static final Logger LOGGER = LoggerFactory.getLogger(ResumeIntakeProcessingWorker.class);
  private static final Duration LEASE = Duration.ofMinutes(10);
  private static final Duration HEARTBEAT = Duration.ofMinutes(1);
  // 每轮最多尝试十次领取；慢模型返回后让出共享线程，不在批内预领后续任务。
  private static final int MAX_CLAIM_ATTEMPTS = 10;
  private static final Duration DRAIN_BUDGET = Duration.ofSeconds(30);
  private final AtomicBoolean stopping = new AtomicBoolean();
  @Resource private ResumeIntakeProcessingPersistenceService persistence;
  @Resource private ResumeIntakeParsingManager parsing;
  @Resource private Clock clock;
  @Resource private PlatformIdGenerator ids;
  @Resource(name = AI) private TaskExecutor executor;
  @Resource private TaskScheduler scheduler;
  @Resource private PlatformExecutorProperties properties;
  private int concurrency;
  private CoalescingTaskDispatcher dispatcher;

  @PostConstruct
  void initialize() {
    this.concurrency = properties.getAiWorkloadSlots().getIntakeParse();
    this.dispatcher = new CoalescingTaskDispatcher(concurrency);
    this.dispatcher.observe(taskMonitor);
  }

  @Scheduled(initialDelayString = "${hr.resume-intake.initial-delay:5000}",
      fixedDelayString = "${hr.resume-intake.poll-delay:2000}")
  @ScheduledTaskInfo(name = "录入解析队列检查", module = "简历录入",
      channels = {"RESUME_INTAKE_PARSE"}, businessTypes = {"RESUME_INTAKE_PARSE"},
      idleBackoffMaxMillis = 60000)
  public void poll() {
    if (shouldStop()) return;
    for (int slot = 0; slot < concurrency; slot++) {
      dispatcher.dispatchPolling("RESUME_INTAKE_PARSE", executor, this::claimAndProcess);
    }
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void available(AsyncTaskAvailable event) {
    if (ResumeIntakeWorkerConstants.PARSE.equals(event.getTaskType())) {
      dispatcher.wakeUp();
      poll();
    }
  }

  private int claimAndProcess() {
    if (shouldStop()) return 0;
    int processed = 0;
    Instant deadline = clock.instant().plus(DRAIN_BUDGET);
    Instant now = clock.instant();
    persistence.expireExhausted(now, 100);
    for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS && !shouldStop(); attempt++) {
      now = clock.instant();
      if (!now.isBefore(deadline)) break;
      var candidates = persistence.findClaimable(now, 1);
      if (candidates.isEmpty() || shouldStop()) break;
      ResumeIntakeProcessing candidate = candidates.getFirst();
      String leaseToken = ids.newId();
      if (persistence.claim(candidate.getId(), candidate.getVersion(), leaseToken, now.plus(LEASE), now) == 1) {
        process(persistence.findProcessing(candidate.getId()), leaseToken);
        processed++;
      }
    }
    return processed;
  }

  @PreDestroy
  public void stop() {
    stopping.set(true);
  }

  private boolean shouldStop() {
    return stopping.get() || Thread.currentThread().isInterrupted();
  }

  void process(ResumeIntakeProcessing processing, String leaseToken) {
    ScheduledFuture<?> heartbeat = null;
    try {
      heartbeat = scheduler.scheduleAtFixedRate(() -> {
        Instant now = clock.instant();
        persistence.renewLease(processing.getId(), leaseToken, now.plus(LEASE), now);
      }, clock.instant().plus(HEARTBEAT), HEARTBEAT);
      var result = parsing.parse(processing);
      var archived = result.archive();
      persistence.succeed(processing.getId(), leaseToken, result.pagesJson(), result.factsJson(),
          result.model().provider(), result.model().modelVersion(), archived.objectKey(),
          archived.sha256(), archived.sizeBytes(), archived.encryptionVersion(), clock.instant());
    } catch (Exception ex) {
      String code = failureCode(ex);
      boolean retryable = retryable(ex, code);
      Instant now = clock.instant();
      Instant retryAt = now.plusSeconds(backoff(processing.getAttemptCount()));
      persistence.fail(processing.getId(), leaseToken, code, retryable,
          retryAt, now);
      if (retryable) scheduler.schedule(this::poll, retryAt.plusMillis(100));
      String validationRule = ex instanceof ContractViolation violation
          ? violation.ruleCode() : "NONE";
      String diagnostic = ex instanceof ContractViolation violation
          ? violation.diagnostic() : "none";
      LOGGER.warn("resume-intake-processing-failed processingId={} code={} validationRule={} diagnostic={} errorType={}",
          processing.getId(), code, validationRule, diagnostic, ex.getClass().getName(),
          SafeLogException.from(ex));
    } finally {
      if (heartbeat != null) heartbeat.cancel(false);
    }
  }

  private static String failureCode(Exception failure) {
    if (failure instanceof ContractViolation violation) return violation.errorCode();
    if (failure instanceof IOException ioFailure) return ResumeExtractionErrorCode.classify(ioFailure);
    return failure instanceof IllegalArgumentException
        ? "INTAKE_PARSE_INVALID" : "INTAKE_PARSE_TEMPORARY_FAILURE";
  }

  private static boolean retryable(Exception failure, String code) {
    if (failure instanceof ContractViolation violation) {
      return AiOutputValidationStatus.SCHEMA_INVALID == violation.validationStatus();
    }
    if (failure instanceof IllegalArgumentException) return false;
    return !(failure instanceof IOException)
        || ResumeExtractionErrorCode.allowsWholeDocumentFallback(code);
  }

  private static long backoff(int attempt) {
    return Math.min(900L, 30L << Math.min(5, Math.max(0, attempt - 1)));
  }
}
