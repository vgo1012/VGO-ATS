package com.ezaigc.hr.intelligence.search.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.SEARCH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.REBUILD_TASK_TYPE;
import static com.ezaigc.hr.talent.api.ResumeFactProjectionSource.TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.logging.RepeatedFailureLogger;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.intelligence.search.application.manager.SearchProjectionApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchTaskApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchStateReplayApplicationManager;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "hr.search.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
/** 搜索投影后台任务处理器，负责轮询或消费异步任务。 */
@ManagedScheduledWorker
public class SearchProjectionWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  @PostConstruct
  void observeScheduledTasks() {
    dispatcher.observe(taskMonitor);
    rebuildDispatcher.observe(taskMonitor);
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchProjectionWorker.class);
  private final RepeatedFailureLogger failures = new RepeatedFailureLogger(LOGGER);
  private static final String PROJECTION_TASK_NAME = "SEARCH_PROJECTION";

  private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
  private final CoalescingTaskDispatcher rebuildDispatcher = new CoalescingTaskDispatcher();
  private final AtomicBoolean stopping = new AtomicBoolean();

  @Resource private AsyncTaskBatchRunner taskBatchRunner;
  @Resource private SearchProjectionApplicationManager manager;
  @Resource private SearchTaskApplicationManager taskManager;
  @Resource private SearchStateReplayApplicationManager stateReplay;
  @Resource(name = SEARCH)
  private TaskExecutor taskExecutor;

  @Scheduled(
      initialDelayString = "${hr.search.initial-delay:3000}",
      fixedDelayString = "${hr.search.poll-delay:3000}")
  @ScheduledTaskInfo(name = "搜索投影与重建检查", module = "搜索", channels = {"SEARCH_PROJECTION", "SEARCH_INDEX_REBUILD"}, businessTypes = {"RESUME_SEARCH_PROJECT", "SEARCH_INDEX_REBUILD"}, idleBackoffMaxMillis = 60000)
  public void poll() {
    if (shouldStop()) return;
    // SEARCH 池预留两个核心线程；各通道最多在途一项，重建不占住增量和状态回放通道。
    dispatcher.dispatchPolling(PROJECTION_TASK_NAME, taskExecutor, this::projectAndReplay);
    rebuildDispatcher.dispatchPolling(REBUILD_TASK_TYPE, taskExecutor, this::rebuild);
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void available(AsyncTaskAvailable notification) {
    if (TASK_TYPE.equals(notification.getTaskType())) {
      dispatcher.wakeUp();
      poll();
    } else if (REBUILD_TASK_TYPE.equals(notification.getTaskType())) {
      rebuildDispatcher.wakeUp();
      poll();
    }
  }

  @PreDestroy
  public void stop() {
    stopping.set(true);
  }

  private int projectAndReplay() {
    if (shouldStop()) return 0;
    int processed = 0;
    try {
      // 每次仅领取一项，排空有上限；Broker 丢失或合并唤醒不影响下一次 SQL 轮询。
      for (int i = 0; i < 50 && !stopping.get() && !Thread.currentThread().isInterrupted(); i++) {
        int count = taskBatchRunner.run(TASK_TYPE, 1, taskManager::process);
        if (count == 0) break;
        processed += count;
      }
      failures.recovered("search-durable-task-poll");
    } catch (RuntimeException failure) {
      failures.failed("search-durable-task-poll", failure);
    }
    if (shouldStop()) return processed;
    try {
      for (int batch = 0; batch < 5 && !shouldStop(); batch++) {
        int count = manager.projectNext(200);
        if (count == 0) break;
        processed += count;
      }
      failures.recovered("search-incremental-projection");
    } catch (RuntimeException failure) {
      // 旧索引故障时仍须领取恢复重建；增量保留原检查点，下一次轮询再试。
      failures.failed("search-incremental-projection", failure);
    }
    if (shouldStop()) return processed;
    // 重建切换别名后下一轮继续回放；数据库终态失败也不影响按实际活动索引恢复。
    try {
      for (int batch = 0; batch < 5 && !shouldStop(); batch++) {
        int count = stateReplay.replayNext(200);
        if (count == 0) break;
        processed += count;
      }
      failures.recovered("search-state-replay");
    } catch (RuntimeException failure) {
      failures.failed("search-state-replay", failure);
    }
    return processed;
  }

  private int rebuild() {
    return shouldStop() ? 0 : taskBatchRunner.run(REBUILD_TASK_TYPE, 1, manager::process);
  }

  private boolean shouldStop() {
    return stopping.get() || Thread.currentThread().isInterrupted();
  }
}
