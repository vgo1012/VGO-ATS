package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobBasisRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultRow;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunPurpose;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultSummaryRow;

/**
 * 匹配运行持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary for match-run snapshots, guarded transitions, and result projections.
 */
public interface MatchRunPersistenceService extends IService<MatchRun> {
  List<MatchJobRow> findJobs(MatchJobQuery query);
  List<MatchJobBasisRow> findJobBases(MatchJobQuery query);
  List<MatchJobResultRow> findJobResults(MatchJobQuery query);
  MatchJobResultSummaryRow findJobResultsSummary(MatchJobQuery query);
  int countVisibleJobBasis(MatchJobQuery query);
  List<String> findOwnedPendingResultCandidateIds(String runId, String actorId);
  String findJobRevision(MatchJobQuery query);

  /** 新增运行。 */
  default int insertRun(
      String id,
      String taskId,
      String jdVersionId,
      String jdFactId,
      String planVersionId,
      String criterionPlanId,
      String weightVersionId,
      String preferenceId,
      String selectionId,
      String selectedIds,
      String sortMode,
      int count,
      String ownerUserId,
      String ownerDepartmentId,
      Instant now) {
    return insertRun(id, taskId, jdVersionId, jdFactId, planVersionId, criterionPlanId,
        weightVersionId, preferenceId, MatchRunPurpose.CUSTOM.getCode(), selectionId, selectedIds,
        sortMode, count, ownerUserId, ownerDepartmentId, now);
  }

  /** 新增带明确用途的运行。 */
  int insertRun(
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
      Instant now);

  /** 查询运行。 */
  MatchRun findRun(String id);

  /** 查询运行操作人任务。 */
  MatchRun findRunByTask(String taskId);

  /** 锁定精确绑定的运行，用于共享任务终态事务。 */
  MatchRun findBoundRunForUpdate(String id, String taskId);

  /** 无执行器时收敛业务终态，保留已提交的评分结果。 */
  int terminateWithoutWorker(String id, String taskId, MatchRunStatus status, int succeeded,
      int failed, String errorCode, Instant now, String actorId);

  /** 启动或接管运行；调用方须先在同一事务锁定当前有效任务租约。 */
  int startRun(String id, Instant now);

  /** 完成运行。 */
  int finishRun(
      String id,
      MatchRunStatus status,
      int succeeded,
      int failed,
      String errorCode,
      Instant now);

  int cancelQueuedRun(String id, Instant now);

  int upsertItemOutcome(
      String runId,
      String candidateId,
      String resumeVersionId,
      String status,
      String errorCode,
      int attempt,
      Instant now);

  MatchRun findRunByBase(String baseId);

  int insertBase(
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
      Instant now);

  int upsertResult(
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
      Instant now);
}
