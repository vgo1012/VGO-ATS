package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAINTENANCE;
import static com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager.INTAKE_ADOPTION_TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import org.springframework.transaction.event.TransactionalEventListener;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.TaskRetryBackoff;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 使用既有任务租约和 Outbox，将已解析结果归档到正式简历版本。
 */
@Component
@ConditionalOnProperty(name = "hr.resume-intake.worker-enabled", havingValue = "true", matchIfMissing = true)
@ManagedScheduledWorker
public class ResumeIntakeFactAdoptionWorker {

    @Resource
    private ScheduledTaskMonitor taskMonitor;

    @PostConstruct
    void observeScheduledTasks() {
        dispatcher.observe(taskMonitor);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ResumeIntakeFactAdoptionWorker.class);
    @Resource
    private AsyncTaskBatchRunner runner;
    @Resource
    private AsyncTaskPort tasks;
    @Resource
    private ResumeParsingApplicationManager parsing;
    @Resource
    private ResumeIntakeProcessingPersistenceService processing;
    @Resource
    private BackgroundActorExecutor actors;
    @Resource
    private Clock clock;
    @Resource(name = MAINTENANCE)
    private TaskExecutor executor;
    private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();

    @Scheduled(initialDelayString = "${hr.resume-intake.initial-delay:5000}",
            fixedDelayString = "${hr.resume-intake.poll-delay:2000}")
    @ScheduledTaskInfo(name = "录入事实采纳轮询", module = "简历录入", channels = {"RESUME_INTAKE_ADOPT"}, businessTypes = {
            "RESUME_INTAKE_ADOPT"}, idleBackoffMaxMillis = 60000)
    public void poll() {
        dispatcher.dispatchPolling(INTAKE_ADOPTION_TASK_TYPE, executor,
                () -> runner.run(INTAKE_ADOPTION_TASK_TYPE, 1, this::process));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void available(AsyncTaskAvailable event) {
        if (INTAKE_ADOPTION_TASK_TYPE.equals(event.getTaskType())) {
            dispatcher.wakeUp();
            poll();
        }
    }

    void process(TaskSnapshot task) {
        try {
            actors.execute(task.requestedByUserId(), () -> {
                parsing.processIntakeAdoption(task, processing.findByResumeVersion(task.resourceId()));
                return null;
            });
        } catch (RuntimeException failure) {
            LOGGER.error("resume-intake-adoption-failed taskId={} attemptNo={}",
                    task.id(), task.attemptCount(), SafeLogException.from(failure));
          if (tasks.shouldStopProcessing(task)) {
            return;
          }
            boolean retryable = !(failure instanceof IllegalArgumentException)
                    && (!(failure instanceof PlatformException platformFailure) || platformFailure.recoverable());
            String code = failure instanceof PlatformException platformFailure
                    ? platformFailure.code()
                    : retryable ? "INTAKE_ADOPTION_TEMPORARY_FAILURE" : "INTAKE_ADOPTION_INVALID";
            String detail = failure instanceof PlatformException ? failure.getMessage()
                    : retryable ? "简历事实归档暂时失败，系统稍后自动重试" : "简历事实归档数据无效，请人工核对";
            if (retryable) {
                tasks.failAndRetryAt(task, code, detail,
                        TaskRetryBackoff.nextRetryAt(clock.instant(), task.attemptCount()));
            } else {
                tasks.fail(task, code, detail, false);
            }
        }
    }
}
