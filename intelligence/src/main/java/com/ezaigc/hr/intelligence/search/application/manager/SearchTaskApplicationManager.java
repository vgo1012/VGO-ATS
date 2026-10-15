package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.talent.api.ResumeFactProjectionSource.TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxDeliveryPort;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.TaskRetryBackoff;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.talent.api.enums.ResumeSearchResourceType;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 持久意图只指向候选人；每次尝试重新读取当前业务快照，不执行消息携带的旧内容。 */
@Component
public class SearchTaskApplicationManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchTaskApplicationManager.class);
  @Resource private AsyncTaskPort tasks;
  @Resource private OutboxDeliveryPort deliveries;
  @Resource private ResumeSearchStateSource source;
  @Resource private SearchDocumentFactory factory;
  @Resource private SearchIndexPort index;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @Resource private Clock clock;

  @Transactional(propagation = Propagation.NEVER)
  public void process(TaskSnapshot task) {
    if (!TASK_TYPE.equals(task.taskType()) || !ResumeSearchResourceType.CANDIDATE.matches(task.resourceType())) {
      throw new IllegalArgumentException("a candidate search projection task is required");
    }
    String previousTrace = MDC.get("traceId");
    try {
      if (tasks.shouldStopProcessing(task)) return;
      OutboxEnvelope envelope;
      try {
        envelope = deliveries.forTask(task);
      } catch (IllegalArgumentException invalidBinding) {
        tasks.fail(task, "SEARCH_TASK_BINDING_INVALID", "搜索任务的持久事件绑定不完整，请人工核对", false);
        return;
      }
      MDC.put("traceId", envelope.getTraceId());
      var current = source.currentState(task.resourceId());
      if (current == null) {
        tasks.fail(task, "SEARCH_CANDIDATE_NOT_FOUND", "搜索任务关联的候选人不存在，请人工核对", false);
        return;
      }
      var document = factory.create(current);
      if (tasks.shouldStopProcessing(task)) return;
      // 批准读取在业务内容之后；迟到 ES alias 不能授权增量写入。
      var target = authority.currentTarget();
      index.ensureApprovedIndex(target);
      if (tasks.shouldStopProcessing(task)) return;
      index.upsertBatch(target, List.of(document));
      tasks.succeed(task, current.candidateId());
    } catch (RuntimeException failure) {
      LOGGER.error("search-task-failed taskId={} attemptNo={}", task.id(), task.attemptCount(),
          SafeLogException.from(failure));
      if (!tasks.shouldStopProcessing(task)) {
        tasks.failAndRetryAt(task, "SEARCH_PROJECTION_FAILED", "搜索索引暂时无法更新，稍后重试",
            TaskRetryBackoff.nextRetryAt(clock.instant(), task.attemptCount()));
      }
    } finally {
      if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId", previousTrace);
    }
  }
}
