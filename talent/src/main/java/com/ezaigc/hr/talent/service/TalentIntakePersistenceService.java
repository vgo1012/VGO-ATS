package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.dto.IntakeScoringPreparationDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 人才接收持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary used by talent-intake managers and query contracts.
 */
public interface TalentIntakePersistenceService extends IService<ResumeIntake> {
  List<String> findCandidateIdsForJdVersion(String jdVersionId, String afterId, int limit);
  List<CandidateResumeRow> findCurrentCandidateResumes(List<String> candidateIds);

  /** 全部简历版本的收件岗位关联；仅用于已实时核验共享资格的候选人历史。 */
  List<CandidateResumeRow> findCandidateResumeHistory(List<String> candidateIds);

  /** 每名候选人的当前业务简历版本，不依赖收件关联岗位或评分状态。 */
  List<CandidateResumeRow> findCurrentCandidateResumeVersions(List<String> candidateIds);

  /** 递增批次。 */
  int incrementBatch(String id, int succeeded, int failed, String actorId, Instant now);

  /** 完成批次。 */
  int completeBatch(String id, String actorId, Instant now);

  /** 新增接收。 */
  int insertIntake(
      String id,
      String batchId,
      String channelId,
      String actorId,
      String ownerId,
      String departmentId,
      String fileName,
      String mediaType,
      long size,
      String sha256,
      String objectKey,
      String scanStatus,
      String status,
      String failureCode,
      Instant now);

  /** 查询接收。 */
  ResumeIntake findIntake(String id);

  /** 统计同过滤条件及可见范围内的全部收件。 */
  long countIntakes(
      String batchId, String status, String importStatus, Boolean duplicateContent, String query, String name,
      Instant from, Instant to,
      String actorId, boolean scopeAll, Set<String> managedUserIds, Set<String> visibleDepartmentIds);

  /** 查询接收记录列表。 */
  List<ResumeIntake> findIntakes(
      String batchId,
      String status,
      String importStatus,
      Boolean duplicateContent,
      String query,
      String name,
      Instant from,
      Instant to,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 分配接收。 */
  int assignIntake(
      String id, String ownerId, String departmentId, long version, String actorId, Instant now);

  /** 更新接收状态。 */
  int updateIntakeStatus(
      String id, String status, String failureCode, long version, String actorId, Instant now);

  /** 更新接收对象键。 */
  int updateIntakeObjectKey(String id, String objectKey, String actorId, Instant now);

  /** 关联按内容共享的后台解析处理。 */
  int attachProcessing(String id, String processingId, String actorId, Instant now);

  /** 查询等待消费某共享处理结果的收件。 */
  List<ResumeIntake> findPendingIntakes(String processingStatus, int limit);

  /** 为共享处理选择最早一条已绑定 JD 的收件，供首次联合解析评分使用。 */
  IntakeScoringPreparationDTO findScoringPreparationIntake(String processingId);

  /** 查询事实已就绪且等待评分提交或恢复的收件。 */
  List<ResumeIntake> findScorableIntakes(int limit);

  /** 查询已确认但共享事实尚待导入的收件。 */
  List<ResumeIntake> findAdoptingIntakes(int limit);

  /** 仅在预期状态下推进后台收件状态。 */
  int advanceIntake(String id, String expectedStatus, String nextStatus, String failureCode,
      String actorId, Instant now);

  /** 仅切换一条身份待核对收件的解析绑定，保留共享成功结果。 */
  int reparseIntake(String id, long version, String oldProcessingId, String newProcessingId,
      String actorId, Instant now);

  /** 保存岗位评分运行投影。 */
  int updateScore(String id, long expectedVersion, String runId, String scoreStatus, String failureCode,
      String actorId, Instant now);

  /** 确认接收。 */
  int confirmIntake(
      String id,
      String candidateId,
      String resumeVersionId,
      long version,
      String actorId,
      Instant now);

  /** 查询候选人列表。 */
  List<Candidate> findCandidates(
      String query,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      boolean readyOnly,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 按精确名称查询候选人列表。 */
  List<Candidate> findCandidatesByExactName(
      String name,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds);

  /** 删除重复被合并身份列表。 */
  int deleteDuplicateMergedIdentities(String survivorId, String mergedId);

  /** 查询最大简历版本。 */
  int findMaxResumeVersion(String resumeId);

  /** 查询候选人简历版本。 */
  ResumeVersion findCandidateResumeVersion(String candidateId, String resumeVersionId);

  /** 查询简历版本列表。 */
  List<ResumeVersion> findResumeVersions(
      String candidateId, Integer cursorVersionNo, String cursorId, int limit);

  int moveResumeVersions(String survivorResumeId, String mergedResumeId, int offset);

  int redirectCandidateIntakes(String survivorId, String mergedId, String actorId, Instant now);

  /** 查询冲突列表。 */
  List<IdentityConflict> findConflicts(
      String status,
      List<String> intakeIds,
      String candidateId,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
