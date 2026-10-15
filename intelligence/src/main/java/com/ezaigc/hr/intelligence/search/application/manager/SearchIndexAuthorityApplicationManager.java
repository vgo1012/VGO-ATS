package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.PROJECTION_EPOCH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.REBUILD_TASK_TYPE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_APPROVED_INDEX;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_PROJECTION;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.config.SearchIndexRetentionProperties;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildResourceType;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 唯一业务路由权威来自独立 MySQL 批准行；远端别名仅参与首次受控初始化。 */
@Component
public class SearchIndexAuthorityApplicationManager {
  @Resource private ProjectionCheckpointService checkpoints;
  @Resource private StateReplayCheckpointService stateCheckpoints;
  @Resource private RebuildRunService rebuilds;
  @Resource private AsyncTaskPort tasks;
  @Resource private SearchIndexPort index;
  @Resource private Clock clock;
  @Resource private TransactionTemplate transactionTemplate;
  @Resource private SearchIndexResourceApplicationManager resources;
  @Resource private SearchIndexRetentionProperties retention;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SearchIndexTarget currentTarget() {
    long deadline = System.nanoTime() + retention.getRequestBudget().toNanos();
    SearchIndexTarget approved = readApproval();
    if (approved == null) {
      // 仅行不存在时核验一次候选；已有 NULL 行同样不能从 alias 自动补成批准目标。
      String candidate = index.activeIndex();
      if (candidate == null) {
        initializeObserved();
        initializeApproval(null);
        String created = resources.createBootstrap();
        transactionTemplate.executeWithoutResult(ignored -> {
          var initial = checkpoints.lockApprovedIndex();
          resources.requireReady(created, null, 0);
          if (initial == null || initial.indexName() != null || initial.generation() != 0
              || !checkpoints.approveIndex(created, 0, SYSTEM_ACTOR_ID, clock.instant())
              || !checkpoints.resetForApprovedIndex(created, PROJECTION_EPOCH, SYSTEM_ACTOR_ID, clock.instant())
              || !stateCheckpoints.resetForApprovedIndex(created, SYSTEM_ACTOR_ID, clock.instant())) {
            throw new IllegalStateException("search bootstrap approval changed");
          }
        });
      } else {
        boolean trusted = index.isManagedBootstrap(candidate) || successfulTarget(candidate);
        initializeApproval(trusted ? candidate : null);
      }
      approved = readApproval();
    }
    if (approved == null || approved.indexName() == null) {
      throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_INDEX_NOT_APPROVED",
          "搜索索引尚未批准，请完成受控重建后重试", true);
    }
    if (approved.generation() < 0 || approved.generation() > Integer.MAX_VALUE) {
      throw new IllegalStateException("invalid search approval generation");
    }
    return pinApproval(deadline);
  }

  /** 重建可恢复未知旧 alias；先建立空批准行，不把它解释为成功或空检索结果。 */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SearchIndexTarget rebuildBase() {
    initializeObserved();
    SearchIndexTarget approved = readApproval();
    if (approved == null) {
      initializeApproval(null);
      approved = readApproval();
    }
    if (approved == null) throw new IllegalStateException("search approval unavailable");
    return approved;
  }

  private SearchIndexTarget readApproval() {
    // 本次当前读事务先结束，远端索引操作不会持有锁或复用外层历史快照。
    TransactionTemplate currentRead = new TransactionTemplate(transactionTemplate.getTransactionManager());
    currentRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    // MySQL 的只读事务禁止 FOR SHARE 锁定；这里只读数据，但事务必须允许当前读锁。
    currentRead.setReadOnly(false);
    return currentRead.execute(ignored -> checkpoints.approvedIndex());
  }

  private SearchIndexTarget pinApproval(long deadline) {
    TransactionTemplate currentRead = new TransactionTemplate(transactionTemplate.getTransactionManager());
    currentRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    currentRead.setReadOnly(false);
    return currentRead.execute(ignored -> {
      var current = checkpoints.lockApprovedIndex();
      if (current == null || current.indexName() == null) {
        throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_INDEX_NOT_APPROVED",
            "搜索索引尚未批准，请完成受控重建后重试", true);
      }
      return resources.pin(current, deadline);
    });
  }

  private boolean successfulTarget(String candidate) {
    var run = rebuilds.findSucceededTarget(candidate);
    if (run == null || !candidate.equals(run.getTargetIndex())
        || !SearchRebuildStatus.SUCCEEDED.matches(run.getStatus())) return false;
    TaskSnapshot task;
    try {
      task = tasks.get(run.getTaskId());
    } catch (PlatformException missingTask) {
      if (missingTask.status().value() == HttpStatus.NOT_FOUND.value()) return false;
      throw missingTask;
    }
    return task != null && AsyncTaskStatus.SUCCEEDED.matches(task.status())
        && run.getTaskId().equals(task.id()) && REBUILD_TASK_TYPE.equals(task.taskType())
        && SearchRebuildResourceType.SEARCH_INDEX_REBUILD.matches(task.resourceType())
        && run.getId().equals(task.resourceId());
  }

  private void initializeApproval(String indexName) {
    ProjectionCheckpoint row = projectionRow(RESUME_FACTS_APPROVED_INDEX);
    row.setIndexName(indexName);
    checkpoints.initialize(row);
  }

  private void initializeObserved() {
    checkpoints.initialize(projectionRow(RESUME_FACTS_PROJECTION));
    StateReplayCheckpoint row = new StateReplayCheckpoint();
    row.setProjectionName(RESUME_FACTS_PROJECTION);
    row.setLastCandidateId(MIN_ID);
    row.setCreateId(SYSTEM_ACTOR_ID);
    row.setCreateTime(clock.instant());
    row.setUpdateId(SYSTEM_ACTOR_ID);
    row.setUpdateTime(clock.instant());
    stateCheckpoints.initialize(row);
  }

  private ProjectionCheckpoint projectionRow(String name) {
    ProjectionCheckpoint row = new ProjectionCheckpoint();
    row.setProjectionName(name);
    row.setLastCreatedAt(PROJECTION_EPOCH);
    row.setLastSnapshotId(MIN_ID);
    row.setCreateId(SYSTEM_ACTOR_ID);
    row.setCreateTime(clock.instant());
    row.setUpdateId(SYSTEM_ACTOR_ID);
    row.setUpdateTime(clock.instant());
    return row;
  }
}
