package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.REBUILD_TASK_TYPE;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.infrastructure.logging.RepeatedFailureLogger;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.version.ResourceVersion;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.config.SearchIndexRetentionProperties;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.entity.SearchIndexResource;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.domain.request.SearchIndexReconciliationRequest;
import com.ezaigc.hr.intelligence.search.domain.response.LegacySearchIndexTargetResponse;
import com.ezaigc.hr.intelligence.search.domain.response.SearchIndexResourceResponse;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceKind;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceStatus;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildResourceType;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.SearchIndexResourceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 创建请求未决保护、请求 pin 与持久删除意图；所有远端 IO 在短事务之外。 */
@Component
public class SearchIndexResourceApplicationManager {
  private final RepeatedFailureLogger deletionFailures = new RepeatedFailureLogger(
      LoggerFactory.getLogger(SearchIndexResourceApplicationManager.class));
  @Resource private SearchIndexResourceService resources;
  @Resource private ProjectionCheckpointService checkpoints;
  @Resource private RebuildRunService rebuilds;
  @Resource private AsyncTaskPort tasks;
  @Resource private SearchIndexPort index;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private SearchIndexRetentionProperties policy;
  @Resource private TransactionTemplate transactionTemplate;
  @Resource private AuthorizationService authorization;

  /** 调用者先持有批准行当前锁，本事务结束后才允许查询/增量 IO。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public SearchIndexTarget pin(SearchIndexTarget approved, long deadlineNanos) {
    SearchIndexResource resource = resources.lock(approved.indexName());
    if (resource == null) {
      // 已受控批准的历史目标可读，但没有远端 owned marker 前不准自动删除。
      if (resources.liveCount() >= policy.getMaxLiveResources()) {
        throw unavailable("SEARCH_INDEX_CAPACITY_BLOCKED", "搜索资源容量已满，请先回收历史目标");
      }
      var origin = rebuilds.findSucceededTarget(approved.indexName());
      TaskSnapshot originTask = origin == null ? null : tasks.get(origin.getTaskId());
      if (origin != null && (originTask == null || !Objects.equals(origin.getId(), originTask.resourceId())
          || !REBUILD_TASK_TYPE.equals(originTask.taskType())
          || !SearchRebuildResourceType.SEARCH_INDEX_REBUILD.matches(originTask.resourceType()))) {
        throw unavailable("SEARCH_RESOURCE_ORIGIN_CHANGED", "已批准历史目标的原任务归属不匹配");
      }
      resource = row(approved.indexName(), SearchIndexResourceKind.LEGACY_APPROVED,
          origin == null ? null : origin.getId(), originTask == null ? null : originTask.id(),
          originTask == null ? 0 : originTask.attemptCount());
      resource.setStatus(SearchIndexResourceStatus.READY.getCode());
      resource.setManaged(false);
      resource.setReadyAt(clock.instant());
      save(resource);
    }
    requireScope(resource);
    if (!SearchIndexResourceStatus.READY.matches(resource.getStatus())
        || !resources.pin(resource.getId(), policy.getRequestBudget().toNanos() / 1000)) {
      throw unavailable("SEARCH_INDEX_RETIRING", "搜索目标已退役或创建结果尚未核对");
    }
    return approved.withDeadline(deadlineNanos);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void createRebuild(TaskSnapshot task, SearchRebuildAttempt attempt) {
    SearchIndexResource reserved = transactionTemplate.execute(ignored -> {
      if (!tasks.renewLease(task)) throw unavailable("SEARCH_REBUILD_STOPPED", "搜索重建批次已停止");
      requireApprovalLock();
      var run = rebuilds.getById(attempt.runId());
      if (run == null || !task.id().equals(run.getTaskId()) || !attempt.indexName().equals(run.getTargetIndex())
          || !SearchRebuildStatus.RUNNING.matches(run.getStatus()) || attempt.attempt() != task.attemptCount()) {
        throw unavailable("SEARCH_REBUILD_STOPPED", "搜索重建批次已经变化");
      }
      return reserve(attempt.indexName(), SearchIndexResourceKind.REBUILD, attempt.runId(), task.id(), attempt.attempt());
    });
    if (SearchIndexResourceStatus.READY.matches(reserved.getStatus())) return;
    try {
      index.createRebuildTarget(attempt, reserved.getId());
      acknowledge(reserved, true);
    } catch (RuntimeException failure) {
      preserveUnknown(reserved, failure);
      throw failure;
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public String createBootstrap() {
    String name = "resume-facts-v" + ids.newId().toLowerCase(Locale.ROOT);
    SearchIndexResource reserved = transactionTemplate.execute(ignored -> {
      SearchIndexTarget approved = requireApprovalLock();
      if (approved.indexName() != null || approved.generation() != 0) {
        throw unavailable("SEARCH_BOOTSTRAP_CHANGED", "搜索初始批准已经变化");
      }
      return reserve(name, SearchIndexResourceKind.BOOTSTRAP, null, null, 0);
    });
    try {
      index.createBootstrap(name, reserved.getId());
      acknowledge(reserved, true);
      return name;
    } catch (RuntimeException failure) {
      preserveUnknown(reserved, failure);
      throw failure;
    }
  }

  private SearchIndexResource reserve(String name, SearchIndexResourceKind kind, String runId, String taskId, int attempt) {
    // 重复调用绝不重发同一显式创建；已知 READY 可由调用者仅 prepare/write。
    var existing = resources.lock(name);
    if (existing != null) {
      requireScope(existing);
      if (SearchIndexResourceStatus.READY.matches(existing.getStatus())
          && Objects.equals(taskId, existing.getTaskId()) && Objects.equals(runId, existing.getRunId())
          && attempt == existing.getAttemptCount()) return existing;
      throw unavailable("SEARCH_CREATE_ALREADY_REGISTERED", "搜索创建意图已登记，禁止再次创建");
    }
    requireCapacity();
    SearchIndexResource resource = row(name, kind, runId, taskId, attempt);
    save(resource);
    return resource;
  }

  private void requireCapacity() {
    if (resources.unresolvedCount() >= policy.getMaxUnresolvedCreates()
        || resources.liveCount() >= policy.getMaxLiveResources()) {
      throw unavailable("SEARCH_INDEX_CAPACITY_BLOCKED", "搜索资源容量已满，请先核对未决创建或回收历史目标");
    }
  }

  private void acknowledge(SearchIndexResource intent, boolean managed) {
    transactionTemplate.executeWithoutResult(ignored -> {
      requireApprovalLock();
      SearchIndexResource current = requireIntent(intent);
      if (!SearchIndexResourceStatus.CREATING.matches(current.getStatus())
          && !SearchIndexResourceStatus.CREATE_UNKNOWN.matches(current.getStatus())) {
        throw new IllegalStateException("search create intent already resolved");
      }
      current.setStatus(SearchIndexResourceStatus.READY.getCode());
      current.setManaged(managed);
      current.setReadyAt(clock.instant());
      update(current);
    });
  }

  private void unknown(SearchIndexResource intent) {
    transactionTemplate.executeWithoutResult(ignored -> {
      requireApprovalLock();
      SearchIndexResource current = requireIntent(intent);
      if (SearchIndexResourceStatus.CREATING.matches(current.getStatus())) {
        current.setStatus(SearchIndexResourceStatus.CREATE_UNKNOWN.getCode());
        update(current);
      }
    });
  }

  private void preserveUnknown(SearchIndexResource intent, RuntimeException failure) {
    try { unknown(intent); }
    catch (RuntimeException persistenceFailure) { failure.addSuppressed(persistenceFailure); }
  }

  /** 供同一批准短事务核验目标已确定创建，不接受正在退役或未知的资源。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireReady(String name, String taskId, int attempt) {
    SearchIndexResource resource = resources.lock(name);
    if (resource == null || !SearchIndexResourceStatus.READY.matches(resource.getStatus())
        || !Objects.equals(taskId, resource.getTaskId()) || attempt != resource.getAttemptCount()) {
      throw unavailable("SEARCH_INDEX_NOT_READY", "搜索目标未确定创建或已经退役");
    }
    requireScope(resource);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public int collect() {
    int deleted = 0;
    int attempts = 0;
    for (SearchIndexResource candidate : resources.candidates(policy.getMinimumAge().toSeconds(), policy.getMaxLiveResources())) {
      if (attempts >= policy.getBatchSize()) break;
      SearchIndexResource intent = transactionTemplate.execute(ignored -> {
        var approved = requireApprovalLock();
        SearchIndexResource current = resources.lock(candidate.getIndexName());
        if (current == null || Objects.equals(approved.indexName(), current.getIndexName()) || !current.isManaged()) return null;
        requireScope(current);
        if (SearchIndexResourceStatus.RETIRING.matches(current.getStatus())) return current;
        if (!SearchIndexResourceStatus.READY.matches(current.getStatus())
            || resources.newest(policy.getRetainCount()).contains(current.getIndexName())
            || !terminal(current) || !resources.retiring(current.getId(), current.getVersion(), SYSTEM_ACTOR_ID)) return null;
        current.setStatus(SearchIndexResourceStatus.RETIRING.getCode());
        return current;
      });
      if (intent == null) continue;
      attempts++;
      String operation = "search-index-retire-" + intent.getId();
      try {
        index.retireIndex(intent.getIndexName(), intent.getId());
        transactionTemplate.executeWithoutResult(ignored -> {
          var approved = requireApprovalLock();
          SearchIndexResource current = requireIntent(intent);
          if (Objects.equals(approved.indexName(), current.getIndexName())
              || !SearchIndexResourceStatus.RETIRING.matches(current.getStatus())) throw new IllegalStateException("search retirement changed");
          current.setStatus(SearchIndexResourceStatus.DELETED.getCode());
          update(current);
        });
        deletionFailures.recovered(operation);
        deleted++;
      } catch (RuntimeException failure) {
        // 意图保持 RETIRING；按远端尝试计批次，单目标失败不饿死后续资源。
        deletionFailures.failed(operation, failure);
      }
    }
    return deleted;
  }

  private boolean terminal(SearchIndexResource resource) {
    if (resource.getTaskId() == null) return SearchIndexResourceKind.BOOTSTRAP.matches(resource.getResourceKind());
    TaskSnapshot task = tasks.get(resource.getTaskId());
    if (!REBUILD_TASK_TYPE.equals(task.taskType()) || !SearchRebuildResourceType.SEARCH_INDEX_REBUILD.matches(task.resourceType())
        || !Objects.equals(resource.getRunId(), task.resourceId())) return false;
    return AsyncTaskStatus.SUCCEEDED.matches(task.status()) || AsyncTaskStatus.CANCELLED.matches(task.status())
        || AsyncTaskStatus.FAILED.matches(task.status()) && !task.retryable();
  }

  public List<LegacySearchIndexTargetResponse> legacyTargets(String runId) {
    requireAdministrator();
    RebuildRun run = requireRun(runId);
    TaskSnapshot task = authorizeTask(run.getTaskId(), runId);
    if (task.attemptCount() < 0 || task.attemptCount() > task.maxAttempts() || task.attemptCount() > 100) {
      throw new IllegalStateException("invalid legacy search attempt count");
    }
    List<LegacySearchIndexTargetResponse> result = new ArrayList<>();
    for (int attempt = 1; attempt <= task.attemptCount(); attempt++) {
      result.add(new LegacySearchIndexTargetResponse(new SearchRebuildAttempt(runId, attempt).indexName(),
          attempt, ResourceVersion.format(run.getVersion()), task.id(), task.status()));
    }
    return List.copyOf(result);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SearchIndexResourceResponse adoptLegacy(String runId, int attempt, SearchIndexReconciliationRequest request) {
    String actor = requireAdministrator();
    long expectedVersion = validateReconciliation(request);
    SearchIndexResource intent = transactionTemplate.execute(ignored -> {
      RebuildRun run = requireRun(runId);
      TaskSnapshot task = authorizeTask(run.getTaskId(), runId);
      // cancel 获取原 task 当前行锁；若新 worker 已 RUNNING，只请求取消并回滚本次核对。
      task = tasks.cancel(task.id(), request.getReason());
      run = rebuilds.currentForUpdate(runId);
      if (run == null || !task.id().equals(run.getTaskId())) throw conflict("历史搜索记录已经变化");
      if (!AsyncTaskStatus.fromCode(task.status()).isTerminal() || attempt < 1 || attempt > task.attemptCount()
          || run.getVersion().longValue() != expectedVersion) throw conflict("历史搜索批次已经变化，请重新盘点");
      requireApprovalLock();
      String name = new SearchRebuildAttempt(runId, attempt).indexName();
      if (resources.lock(name) != null) throw conflict("该目标已登记，请使用资源核对入口");
      requireCapacity();
      SearchIndexResource resource = row(name, SearchIndexResourceKind.LEGACY_ATTEMPT, runId, task.id(), attempt);
      resource.setStatus(SearchIndexResourceStatus.CREATE_UNKNOWN.getCode());
      recordAudit(resource, actor, request);
      save(resource);
      return resource;
    });
    return completeReconciliation(intent);
  }

  public SearchIndexResourceResponse getResource(String id) {
    requireAdministrator();
    SearchIndexResource resource = requireResource(id);
    authorizeResource(resource);
    return response(resource);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SearchIndexResourceResponse reconcile(String id, SearchIndexReconciliationRequest request) {
    String actor = requireAdministrator();
    long expectedVersion = validateReconciliation(request);
    SearchIndexResource observed = requireResource(id);
    authorizeResource(observed);
    SearchIndexResource intent = transactionTemplate.execute(ignored -> {
      if (observed.getTaskId() != null) {
        TaskSnapshot task = authorizeTask(observed.getTaskId(), observed.getRunId());
        task = tasks.cancel(task.id(), request.getReason());
        if (!AsyncTaskStatus.fromCode(task.status()).isTerminal()) throw conflict("原搜索任务尚未终止");
      }
      requireApprovalLock();
      SearchIndexResource current = requireIntent(observed);
      boolean replay = current.getReconciledAt() != null && actor.equals(current.getReconciledBy())
          && request.getReason().strip().equals(current.getReconciliationReason());
      if (current.getVersion().longValue() != expectedVersion && !replay) throw conflict("搜索资源版本已经变化");
      if (current.getReconciledAt() != null && !replay) throw conflict("同一创建意图的核对审计不可覆盖");
      if (replay && (SearchIndexResourceStatus.DELETED.matches(current.getStatus())
          || SearchIndexResourceStatus.READY.matches(current.getStatus()) && current.isManaged())) return current;
      if (!SearchIndexResourceStatus.CREATING.matches(current.getStatus())
          && !SearchIndexResourceStatus.CREATE_UNKNOWN.matches(current.getStatus())
          && !(SearchIndexResourceStatus.READY.matches(current.getStatus()) && !current.isManaged())) {
        throw conflict("仅允许核对未决创建或尚未纳管的历史批准目标");
      }
      if (current.getReconciledAt() == null) {
        recordAudit(current, actor, request);
        current.setUpdateId(actor);
        update(current);
      }
      return current;
    });
    if (SearchIndexResourceStatus.DELETED.matches(intent.getStatus())
        || SearchIndexResourceStatus.READY.matches(intent.getStatus()) && intent.isManaged()) return response(intent);
    return completeReconciliation(intent);
  }

  private SearchIndexResourceResponse completeReconciliation(SearchIndexResource intent) {
    // 重试只核验/补相同 marker 与 alias，绝不显式 create；原 sender 已终止由管理员负责确认。
    boolean exists = index.adoptIndex(intent.getIndexName(), intent.getId());
    return transactionTemplate.execute(ignored -> {
      var approved = requireApprovalLock();
      SearchIndexResource current = requireIntent(intent);
      if (!exists && Objects.equals(approved.indexName(), current.getIndexName())) {
        throw conflict("批准目标缺失，请重建恢复，不能把批准目标标记已回收");
      }
      if (SearchIndexResourceStatus.RETIRING.matches(current.getStatus())
          || SearchIndexResourceStatus.DELETED.matches(current.getStatus())) throw conflict("核对期间资源已退役");
      current.setStatus(exists ? SearchIndexResourceStatus.READY.getCode() : SearchIndexResourceStatus.DELETED.getCode());
      current.setManaged(exists);
      if (exists && current.getReadyAt() == null) current.setReadyAt(clock.instant());
      current.setUpdateId(current.getReconciledBy());
      update(current);
      return response(current);
    });
  }

  private String requireAdministrator() {
    var actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) throw new PlatformException(HttpStatus.FORBIDDEN,
        "SEARCH_RESOURCE_ADMIN_REQUIRED", "仅超级管理员可以核对和纳管搜索资源", false);
    return actor.getUserId();
  }

  private TaskSnapshot authorizeTask(String taskId, String runId) {
    TaskSnapshot task = tasks.get(taskId);
    if (!REBUILD_TASK_TYPE.equals(task.taskType()) || !SearchRebuildResourceType.SEARCH_INDEX_REBUILD.matches(task.resourceType())
        || !Objects.equals(runId, task.resourceId()) || !Objects.equals(taskId, task.id())) throw conflict("搜索资源与原任务绑定不匹配");
    authorization.requireAccess(task.managePermission(), new AuthorizationTarget("ASYNC_TASK", task.id(),
        task.ownerUserId(), task.ownerDepartmentId(), Set.of(task.requestedByUserId())));
    return task;
  }

  private void authorizeResource(SearchIndexResource resource) {
    if (resource.getTaskId() != null) authorizeTask(resource.getTaskId(), resource.getRunId());
    else authorization.requirePermission("recruitment.manage");
  }

  private RebuildRun requireRun(String id) {
    RebuildRun run = rebuilds.getById(id);
    if (run == null) throw new PlatformException(HttpStatus.NOT_FOUND, "SEARCH_REBUILD_NOT_FOUND", "索引重建记录不存在", false);
    return run;
  }

  private SearchIndexResource requireResource(String id) {
    var resource = resources.getById(id);
    if (resource == null) throw new PlatformException(HttpStatus.NOT_FOUND, "SEARCH_RESOURCE_NOT_FOUND", "搜索资源不存在", false);
    return resource;
  }

  private void recordAudit(SearchIndexResource resource, String actor, SearchIndexReconciliationRequest request) {
    resource.setReconciliationReason(request.getReason().strip()); resource.setReconciledBy(actor);
    resource.setReconciledAt(clock.instant()); resource.setOriginalRequestTerminated(true);
  }

  private static long validateReconciliation(SearchIndexReconciliationRequest request) {
    if (request == null || request.getReason() == null || request.getReason().isBlank() || request.getReason().length() > 500
        || !Boolean.TRUE.equals(request.getOwnershipConfirmed()) || !Boolean.TRUE.equals(request.getOriginalRequestTerminated())) {
      throw new PlatformException(HttpStatus.UNPROCESSABLE_ENTITY, "SEARCH_RESOURCE_CONFIRMATION_REQUIRED",
          "必须提供理由，并确认目标归属及所有原请求已终止", false);
    }
    return ResourceVersion.parse(request.getVersion());
  }

  private static SearchIndexResourceResponse response(SearchIndexResource resource) {
    return new SearchIndexResourceResponse(resource.getId(), resource.getIndexName(), resource.getStatus(), resource.isManaged(),
        ResourceVersion.format(resource.getVersion()), resource.getReconciliationReason(), resource.getReconciledBy(), resource.getReconciledAt());
  }

  private static PlatformException conflict(String detail) {
    return new PlatformException(HttpStatus.CONFLICT, "SEARCH_RESOURCE_CHANGED", detail, true);
  }

  private SearchIndexTarget requireApprovalLock() {
    SearchIndexTarget approved = checkpoints.lockApprovedIndex();
    if (approved == null) throw new IllegalStateException("search approval row required before lifecycle operation");
    return approved;
  }

  private SearchIndexResource requireIntent(SearchIndexResource intent) {
    SearchIndexResource current = resources.lock(intent.getIndexName());
    if (current == null || !intent.getId().equals(current.getId())) throw new IllegalStateException("search resource intent changed");
    requireScope(current);
    return current;
  }

  private void requireScope(SearchIndexResource resource) {
    if (!Objects.equals(index.resourceScope(), resource.getScopeName())) throw unavailable("SEARCH_RESOURCE_SCOPE_CHANGED", "搜索资源后端归属变化，禁止继续操作");
  }

  private SearchIndexResource row(String name, SearchIndexResourceKind kind, String runId, String taskId, int attempt) {
    SearchIndexResource resource = new SearchIndexResource();
    resource.setId(ids.newId()); resource.setIndexName(name); resource.setScopeName(index.resourceScope());
    resource.setResourceKind(kind.getCode()); resource.setRunId(runId); resource.setTaskId(taskId); resource.setAttemptCount(attempt);
    resource.setStatus(SearchIndexResourceStatus.CREATING.getCode());
    resource.setCreateId(SYSTEM_ACTOR_ID); resource.setUpdateId(SYSTEM_ACTOR_ID);
    resource.setCreateTime(clock.instant()); resource.setUpdateTime(clock.instant());
    return resource;
  }

  private void save(SearchIndexResource resource) {
    if (!resources.save(resource)) throw new IllegalStateException("search resource save failed");
  }

  private void update(SearchIndexResource resource) {
    resource.setUpdateTime(clock.instant());
    if (!resources.updateById(resource)) throw new IllegalStateException("search resource CAS failed");
  }

  private static PlatformException unavailable(String code, String detail) {
    return new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, code, detail, true);
  }
}
