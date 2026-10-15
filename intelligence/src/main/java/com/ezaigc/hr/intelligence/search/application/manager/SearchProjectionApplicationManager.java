package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_PROJECTION_PAGE_SIZE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.PROJECTION_EPOCH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.REBUILD_TASK_TYPE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_PROJECTION;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchProjectionAssembler;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.domain.response.RebuildRunResponse;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildResourceType;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 搜索投影应用服务，负责用例编排、权限校验和事务边界。 */
@Component
public class SearchProjectionApplicationManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SearchProjectionApplicationManager.class);

  @Resource private ResumeFactProjectionSource source;
  @Resource private SearchDocumentFactory factory;
  @Resource private SearchIndexPort index;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @Resource private SearchIndexResourceApplicationManager resources;
  @Resource private StateReplayCheckpointService stateCheckpoints;
  @Resource private AsyncTaskPort tasks;
  @Resource private AuthorizationService authorization;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ProjectionCheckpointService checkpoints;
  @Resource private RebuildRunService rebuilds;
  @Resource private SearchProjectionAssembler assembler;
  @Resource private TransactionTemplate transactionTemplate;

  /** 是否已经初始化。 */
  private volatile boolean initialized;

  // 索引端口可能执行远程 HTTP I/O；checkpoint 使用版本 CAS，批量投影期间不得占用数据库事务连接。
  public int projectNext(int limit) {
    initialize();
    ProjectionCheckpoint checkpoint = loadCheckpoint();
    // 先读取 observed 版本，再读批准目标；晚到旧页不能重置/推进新代际进度。
    var approved = authority.currentTarget();
    String target = approved.indexName();
    if (!target.equals(checkpoint.getIndexName())) {
      if (!checkpoints.bindIndex(target, checkpoint.getVersion(), PROJECTION_EPOCH,
          SYSTEM_ACTOR_ID, clock.instant())) return 0;
      checkpoint.setIndexName(target);
      checkpoint.setLastCreatedAt(PROJECTION_EPOCH);
      checkpoint.setLastSnapshotId(MIN_ID);
      checkpoint.setProjectedCount(0);
      checkpoint.setVersion(checkpoint.getVersion() + 1);
    }
    var page =
        source.currentFactsAfter(
            checkpoint.getLastCreatedAt(),
            checkpoint.getLastSnapshotId(),
            Math.clamp(limit, 1, MAX_PROJECTION_PAGE_SIZE));
    if (page.items().isEmpty()) return 0;
    List<SearchDocument> documents =
        page.items().stream()
            .map(factory::create)
            .toList();
    index.upsertBatch(approved, documents);
    var last = page.items().getLast();
    if (!checkpoints.advance(
        last.createTime(),
        last.facts().getSnapshotId(),
        page.items().size(),
        checkpoint.getVersion(),
        SYSTEM_ACTOR_ID,
        clock.instant())) throw new IllegalStateException("search projection checkpoint changed");
    return page.items().size();
  }

  /** 创建重建。 */
  @Transactional
  public RebuildRunResponse createRebuild(String idempotencyKey) {
    var actor = authorization.currentActor();
    if (!actor.isSuperAdmin())
      throw new PlatformException(
          HttpStatus.FORBIDDEN,
          "SEARCH_REBUILD_ADMIN_REQUIRED",
          "仅超级管理员可以重建搜索索引",
          false);
    TaskSnapshot replay = tasks.getByIdempotency(actor.getUserId(), idempotencyKey);
    if (replay != null) {
      if (!REBUILD_TASK_TYPE.equals(replay.taskType()))
        throw new PlatformException(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_KEY_REUSED",
            "同一 Idempotency-Key 不能用于不同任务",
            false);
      return assembler.toResponse(rebuilds.getById(replay.resourceId()));
    }
    Instant now = clock.instant();
    String runId = ids.newId();
    String taskId = ids.newId();
    String target = "resume-facts-v" + runId.toLowerCase(Locale.ROOT);
    TaskSnapshot task =
        tasks.enqueue(
            new EnqueueTask(
                taskId,
                REBUILD_TASK_TYPE,
                SearchRebuildResourceType.SEARCH_INDEX_REBUILD.getCode(),
                runId,
                actor.getUserId(),
                actor.getUserId(),
                actor.getHomeDepartmentId(),
                "talent.read",
                "recruitment.manage",
                idempotencyKey,
                sha("SEARCH_INDEX_REBUILD"),
                3,
                now));
    if (!task.id().equals(taskId))
      return assembler.toResponse(rebuilds.getById(task.resourceId()));

    RebuildRun rebuild = new RebuildRun();
    rebuild.setId(runId);
    rebuild.setTaskId(taskId);
    rebuild.setTargetIndex(target);
    rebuild.setStatus(SearchRebuildStatus.QUEUED.getCode());
    rebuild.setRequestedByUserId(actor.getUserId());
    rebuild.setCreateId(actor.getUserId());
    rebuild.setCreateTime(now);
    rebuild.setUpdateId(actor.getUserId());
    rebuild.setUpdateTime(now);
    if (!rebuilds.save(rebuild)) {
      throw new IllegalStateException("搜索重建记录创建失败, runId=" + runId);
    }
    return assembler.toResponse(rebuild);
  }

  @Transactional(readOnly = true)
  public RebuildRunResponse get(String id) {
    RebuildRun run = rebuilds.getById(id);
    if (run == null)
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "SEARCH_REBUILD_NOT_FOUND", "索引重建任务不存在", false);
    authorization.requirePermission("talent.read");
    return assembler.toResponse(run);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void process(TaskSnapshot task) {
    RebuildRun run = rebuilds.findByTaskId(task.id());
    if (run == null) {
      tasks.fail(task, "SEARCH_REBUILD_NOT_FOUND", "索引重建记录不存在", false);
      return;
    }
    var attempt = new SearchRebuildAttempt(run.getId(), task.attemptCount());
    String target = attempt.indexName();
    if (!startAttempt(task, run.getId(), target)) return;
    long count = 0;
    try {
      SearchIndexTarget base = authority.rebuildBase();
      if (tasks.shouldStopProcessing(task)) {
        completeCancellation(task, run.getId(), count);
        return;
      }
      resources.createRebuild(task, attempt);
      index.beginRebuild(attempt);
      Instant after = PROJECTION_EPOCH;
      String afterId = MIN_ID;
      while (true) {
        if (tasks.shouldStopProcessing(task)) {
          completeCancellation(task, run.getId(), count);
          return;
        }
        var page = source.currentFactsAfter(after, afterId, 200);
        if (page.items().isEmpty()) {
          if (page.hasMore()) {
            throw new IllegalStateException("search projection returned an empty non-terminal page");
          }
          break;
        }
        List<SearchDocument> documents =
            page.items().stream()
                .map(factory::create)
                .toList();
        index.upsertBatch(new SearchIndexTarget(target, 0), documents);
        var last = page.items().getLast();
        count += page.items().size();
        after = last.createTime();
        afterId = last.facts().getSnapshotId();
        if (!page.hasMore()) break;
        tasks.updateProgress(
            task, Math.min(99, Math.max(1, (int) Math.min(95, count / 10 + 1))));
      }
      if (tasks.shouldStopProcessing(task)) {
        completeCancellation(task, run.getId(), count);
        return;
      }
      index.prepareRebuild(attempt);
      // alias 只是外部准备状态；迟到切换不能代替下面的持久批准。
      index.activateRebuild(attempt);
      completeSuccess(task, run.getId(), target, base.generation(), count);
    } catch (RuntimeException ex) {
      LOGGER.error(
          "search-rebuild-failed taskId={} runId={} targetIndex={} indexedCount={}",
          task.id(),
          run.getId(),
          target,
          count,
          SafeLogException.from(ex));
      completeFailure(task, run.getId(), count);
    }
  }

  /** 领取批次使用独立目标；条件续租在短事务内锁住任务行，重复批次不得再次建索引。 */
  private boolean startAttempt(TaskSnapshot task, String runId, String target) {
    return Boolean.TRUE.equals(transactionTemplate.execute(ignored -> {
      if (!tasks.renewLease(task)) {
        // 取消入口同时验证当前批次；失租旧执行器在这里被拒绝，不能写重建记录。
        tasks.cancelled(task);
        RebuildRun latest = rebuilds.getById(runId);
        if (latest == null || !SearchRebuildStatus.CANCELLED.matches(latest.getStatus())) {
          finishRequired(runId, SearchRebuildStatus.CANCELLED, 0, null);
        }
        return false;
      }
      if (rebuilds.start(runId, target, SYSTEM_ACTOR_ID, clock.instant())) return true;
      RebuildRun latest = rebuilds.getById(runId);
      if (latest != null && target.equals(latest.getTargetIndex())
          && SearchRebuildStatus.RUNNING.matches(latest.getStatus())) return false;
      throw new IllegalStateException("搜索重建批次启动失败, runId=" + runId);
    }));
  }

  /** 先由共享任务校验领取批次，再在同一短事务中提交重建终态；索引调用留在事务外。 */
  private void completeSuccess(TaskSnapshot task, String runId, String target,
      long expectedGeneration, long count) {
    transactionTemplate.executeWithoutResult(ignored -> {
      tasks.succeed(task, runId);
      var approved = checkpoints.lockApprovedIndex();
      resources.requireReady(target, task.id(), task.attemptCount());
      if (approved == null || approved.generation() != expectedGeneration
          || !checkpoints.approveIndex(target, expectedGeneration, SYSTEM_ACTOR_ID, clock.instant())
          || !rebuilds.finishApproved(runId, task.id(), target, count, SYSTEM_ACTOR_ID, clock.instant())
          || !checkpoints.resetForApprovedIndex(target, PROJECTION_EPOCH, SYSTEM_ACTOR_ID, clock.instant())
          || !stateCheckpoints.resetForApprovedIndex(target, SYSTEM_ACTOR_ID, clock.instant())) {
        throw new IllegalStateException("搜索索引批准批次或代际冲突, runId=" + runId);
      }
    });
  }

  private void completeCancellation(TaskSnapshot task, String runId, long count) {
    transactionTemplate.executeWithoutResult(ignored -> {
      tasks.cancelled(task);
      finishRequired(runId, SearchRebuildStatus.CANCELLED, count, null);
    });
  }

  private void completeFailure(TaskSnapshot task, String runId, long count) {
    transactionTemplate.executeWithoutResult(ignored -> {
      TaskSnapshot outcome = tasks.fail(task, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true);
      boolean cancelled = AsyncTaskStatus.CANCELLED.matches(outcome.status());
      finishRequired(runId, cancelled ? SearchRebuildStatus.CANCELLED : SearchRebuildStatus.FAILED,
          count, cancelled ? null : "SEARCH_REBUILD_FAILED");
    });
  }

  private void finishRequired(String runId, SearchRebuildStatus status, long count, String errorCode) {
    if (!rebuilds.finish(runId, status, count, errorCode, SYSTEM_ACTOR_ID, clock.instant())) {
      throw new IllegalStateException("搜索重建终态更新失败, runId=" + runId);
    }
  }

  private synchronized void initialize() {
    if (initialized) return;
    Instant now = clock.instant();
    initializeCheckpoint(now);
    index.ensureApprovedIndex(authority.currentTarget());
    if (!index.durable()) checkpoints.reset(PROJECTION_EPOCH, SYSTEM_ACTOR_ID, now);
    initialized = true;
  }

  private ProjectionCheckpoint loadCheckpoint() {
    ProjectionCheckpoint checkpoint = checkpoints.checkpoint();
    if (checkpoint != null) return checkpoint;
    // 数据库重新建表或游标行被清理后，进程内初始化标志仍可能为 true；幂等补建后从头投影。
    initializeCheckpoint(clock.instant());
    checkpoint = checkpoints.checkpoint();
    if (checkpoint == null) {
      throw new IllegalStateException("search projection checkpoint unavailable");
    }
    return checkpoint;
  }

  private void initializeCheckpoint(Instant now) {
    ProjectionCheckpoint checkpoint = new ProjectionCheckpoint();
    checkpoint.setProjectionName(RESUME_FACTS_PROJECTION);
    checkpoint.setLastCreatedAt(PROJECTION_EPOCH);
    checkpoint.setLastSnapshotId(MIN_ID);
    checkpoint.setCreateId(SYSTEM_ACTOR_ID);
    checkpoint.setCreateTime(now);
    checkpoint.setUpdateId(SYSTEM_ACTOR_ID);
    checkpoint.setUpdateTime(now);
    checkpoints.initialize(checkpoint);
  }

  private static String sha(String value) {
    return Sha256Utils.hashUtf8(value);
  }
}
