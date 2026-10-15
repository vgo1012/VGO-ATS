package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobBasisRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultRow;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultSummaryRow;

/**
 * 匹配运行持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation. Business orchestration belongs to application managers.
 */
@Service
public class MatchRunPersistenceServiceImpl extends ServiceImpl<MatchRunMapper, MatchRun>
    implements MatchRunPersistenceService {
  @Override public List<MatchJobRow> findJobs(MatchJobQuery query) { return baseMapper.findJobs(query); }
  @Override public List<MatchJobBasisRow> findJobBases(MatchJobQuery query) { return baseMapper.findJobBases(query); }
  @Override public List<MatchJobResultRow> findJobResults(MatchJobQuery query) { return baseMapper.findJobResults(query); }
  @Override public MatchJobResultSummaryRow findJobResultsSummary(MatchJobQuery query) { return baseMapper.findJobResultsSummary(query); }
  @Override public int countVisibleJobBasis(MatchJobQuery query) { return baseMapper.countVisibleJobBasis(query); }
  @Override public List<String> findOwnedPendingResultCandidateIds(String runId, String actorId) { return baseMapper.findOwnedPendingResultCandidateIds(runId, actorId); }
  @Override public String findJobRevision(MatchJobQuery query) { return baseMapper.findJobRevision(query); }
  /** 新增运行。 */
  @Override
  public int insertRun(
      String id,
      String taskId,
      String jdVersionId,
      String jdFactId,
      String planVersionId,
      String criterionPlanId,
      String weightVersionId,
      String preferenceId,
      String runPurpose,
      String selectionId,
      String selectedIds,
      String sortMode,
      int count,
      String ownerUserId,
      String ownerDepartmentId,
      Instant now) {
    MatchRun run = new MatchRun();
    run.setId(id);
    run.setTaskId(taskId);
    run.setJdVersionId(jdVersionId);
    run.setJdFactSnapshotId(jdFactId);
    run.setPlanVersionId(planVersionId);
    run.setCriterionPlanVersionId(criterionPlanId);
    run.setWeightProfileVersionId(weightVersionId);
    run.setPersonalPreferenceVersionId(preferenceId);
    run.setRunPurpose(runPurpose);
    run.setSelectionSnapshotId(selectionId);
    run.setSelectedCriterionIdsJson(selectedIds);
    run.setSortMode(sortMode);
    run.setStatus(MatchRunStatus.QUEUED.getCode());
    run.setTotalCount(count);
    run.setOwnerUserId(ownerUserId);
    run.setOwnerDepartmentId(ownerDepartmentId);
    run.setCreateId(ownerUserId);
    run.setCreateTime(now);
    run.setUpdateId(ownerUserId);
    run.setUpdateTime(now);
    return baseMapper.insert(run);
  }

  /** 查询运行。 */
  @Override
  public MatchRun findRun(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询运行操作人任务。 */
  @Override
  public MatchRun findRunByTask(String taskId) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(MatchRun.class).eq(MatchRun::getTaskId, taskId));
  }

  /** 锁定精确绑定的运行并读取最新状态。 */
  @Override
  public MatchRun findBoundRunForUpdate(String id, String taskId) {
    return baseMapper.findBoundRunForUpdate(id, taskId);
  }

  /** 终结排队、运行或重试中的业务记录，不覆盖成功及取消终态。 */
  @Override
  public int terminateWithoutWorker(String id, String taskId, MatchRunStatus status, int succeeded,
      int failed, String errorCode, Instant now, String actorId) {
    return baseMapper.update(Wrappers.lambdaUpdate(MatchRun.class)
        .eq(MatchRun::getId, id).eq(MatchRun::getTaskId, taskId)
        .in(MatchRun::getStatus, MatchRunStatus.QUEUED.getCode(), MatchRunStatus.RUNNING.getCode(),
            MatchRunStatus.FAILED.getCode(), MatchRunStatus.PARTIAL.getCode())
        .set(MatchRun::getStatus, status.getCode()).set(MatchRun::getSucceededCount, succeeded)
        .set(MatchRun::getFailedCount, failed).set(MatchRun::getErrorCode, errorCode)
        .set(MatchRun::getFinishedAt, now).setIncrBy(MatchRun::getVersion, 1)
        .set(MatchRun::getUpdateId, actorId).set(MatchRun::getUpdateTime, now));
  }

  /** 完成运行。 */
  @Override
  public int finishRun(
      String id, MatchRunStatus status, int succeeded, int failed, String errorCode, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchRun.class)
            .eq(MatchRun::getId, id)
            .eq(MatchRun::getStatus, MatchRunStatus.RUNNING.getCode())
            .set(MatchRun::getStatus, status.getCode())
            .set(MatchRun::getSucceededCount, succeeded)
            .set(MatchRun::getFailedCount, failed)
            .set(MatchRun::getErrorCode, errorCode)
            .set(MatchRun::getFinishedAt, status == MatchRunStatus.QUEUED ? null : now)
            .setIncrBy(MatchRun::getVersion, 1)
            .set(MatchRun::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(MatchRun::getUpdateTime, now));
  }

  @Override
  public int cancelQueuedRun(String id, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchRun.class)
            .eq(MatchRun::getId, id)
            .eq(MatchRun::getStatus, MatchRunStatus.QUEUED.getCode())
            .set(MatchRun::getStatus, MatchRunStatus.CANCELLED.getCode())
            .set(MatchRun::getFinishedAt, now)
            .setIncrBy(MatchRun::getVersion, 1)
            .set(MatchRun::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(MatchRun::getUpdateTime, now));
  }

  @Override
  public int startRun(String id, Instant now) {
    return baseMapper.startRun(
        id,
        now,
        MatchRunStatus.RUNNING.getCode(),
        MatchRunStatus.QUEUED.getCode(),
        MatchRunStatus.FAILED.getCode(),
        MatchRunStatus.PARTIAL.getCode());
  }

  @Override
  public int upsertItemOutcome(
      String runId,
      String candidateId,
      String resumeVersionId,
      String status,
      String errorCode,
      int attempt,
      Instant now) {
    return baseMapper.upsertItemOutcome(
        runId, candidateId, resumeVersionId, status, errorCode, attempt, now);
  }

  @Override
  public MatchRun findRunByBase(String baseId) {
    return baseMapper.findRunByBase(baseId);
  }

  @Override
  public int insertBase(
      String id,
      String cacheKey,
      String jdFactId,
      String resumeFactId,
      String planVersionId,
      String weightVersionId,
      String criterionHash,
      LocalDate evaluatedOn,
      BigDecimal professional,
      int bonus,
      BigDecimal reference,
      String mustTier,
      int bonusCap,
      String dimensions,
      String criteria,
      Instant now) {
    return baseMapper.insertBase(
        id,
        cacheKey,
        jdFactId,
        resumeFactId,
        planVersionId,
        weightVersionId,
        criterionHash,
        evaluatedOn,
        professional,
        bonus,
        reference,
        mustTier,
        bonusCap,
        dimensions,
        criteria,
        now);
  }

  @Override
  public int upsertResult(
      String id,
      String runId,
      String baseId,
      String candidateId,
      String resumeVersionId,
      BigDecimal professional,
      int bonus,
      BigDecimal reference,
      String mustTier,
      String resultJson,
      Instant now) {
    return baseMapper.upsertResult(
        id,
        runId,
        baseId,
        candidateId,
        resumeVersionId,
        professional,
        bonus,
        reference,
        mustTier,
        resultJson,
        now);
  }
}
