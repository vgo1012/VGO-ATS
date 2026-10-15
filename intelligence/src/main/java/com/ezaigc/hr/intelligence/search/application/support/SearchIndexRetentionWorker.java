package com.ezaigc.hr.intelligence.search.application.support;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.SEARCH;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.logging.RepeatedFailureLogger;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexResourceApplicationManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ManagedScheduledWorker
@ConditionalOnProperty(name = {"hr.search.worker-enabled", "hr.search.retention.enabled"}, havingValue = "true", matchIfMissing = true)
public class SearchIndexRetentionWorker {
  @Resource private SearchIndexResourceApplicationManager resources;
  @Resource private ScheduledTaskMonitor monitor;
  @Resource(name = SEARCH) private TaskExecutor executor;
  private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
  private final AtomicBoolean stopping = new AtomicBoolean();
  private final RepeatedFailureLogger failures = new RepeatedFailureLogger(LoggerFactory.getLogger(SearchIndexRetentionWorker.class));

  @PostConstruct void observe() { dispatcher.observe(monitor); }
  @PreDestroy public void stop() { stopping.set(true); }

  @Scheduled(initialDelayString = "${hr.search.retention.initial-delay:60000}", fixedDelayString = "${hr.search.retention.poll-delay:60000}")
  @ScheduledTaskInfo(name = "搜索旧索引回收", module = "搜索", channels = {"SEARCH_INDEX_RETENTION"}, businessTypes = {"SEARCH_INDEX_REBUILD"}, idleBackoffMaxMillis = 60000)
  public void poll() {
    if (stopping.get()) return;
    dispatcher.dispatchPolling("SEARCH_INDEX_RETENTION", executor, () -> {
      if (stopping.get() || Thread.currentThread().isInterrupted()) return 0;
      try {
        int count = resources.collect();
        failures.recovered("search-index-retention");
        return count;
      } catch (RuntimeException failure) {
        failures.failed("search-index-retention", failure);
        return 0;
      }
    });
  }
}
