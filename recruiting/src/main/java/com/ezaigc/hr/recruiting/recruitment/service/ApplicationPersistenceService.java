package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountQuery;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItemRow;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 应聘流程持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary used by recruitment application managers and same-JVM contracts.
 */
public interface ApplicationPersistenceService extends IService<Application> {
  /** 全部版本、全部所属人的未终态招聘流程；仅供已授权的 JD 删除完整性检查。 */
  boolean hasActiveApplicationsForJd(String jdId);


  /** 调用方持有候选人行锁；包含当前及终态历史。 */
  boolean hasMergeProtectedHistory(String candidateId);

  List<ApplicationStageCountDTO> countStages(ApplicationStageCountQuery query);

  boolean hasRecruitmentHistory(String resumeVersionId);
  boolean hasActiveRecruitment(String resumeVersionId);
  int enableProgressiveStage(String id, String stage, long version, String actorId, Instant now);

  List<Application> findForCandidates(List<String> candidateIds);
  Application findActiveForCandidateAndJd(String candidateId, String jdId);

  /** 新增应聘流程。 */
  int insertApplication(
      String id,
      String candidateId,
      String demandId,
      String jdId,
      String jdVersionId,
      String resumeVersionId,
      String workflowId,
      String matchPlanId,
      String recruitmentType,
      String ownerUserId,
      String departmentId,
      String actorId,
      Instant now);

  /** 查询应聘流程。 */
  Application findApplication(String id);

  Application lockApplication(String id);

  String findJdVersionId(String id);
  String findCandidateId(String id);

  List<Application> findCreatedInRange(Instant from, Instant to, String jdId,
      String jdVersionId, String afterId, int limit);

  /** 查询应聘流程列表。 */
  List<Application> findApplications(
      String jdId,
      String jdVersionId,
      String ownerUserId,
      String stage,
      String workflowStepCode,
      String lifecycle,
      Instant from,
      Instant to,
      String interviewType,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 查询包含入职中记录的入职流程列表。 */
  List<Application> findApplications(
      String jdId,
      String jdVersionId,
      String ownerUserId,
      String stage,
      String workflowStepCode,
      String lifecycle,
      Instant from,
      Instant to,
      String interviewType,
      boolean onboarding,
      Instant cursorTime,
      String cursorId,
      int limit);

  int markOfferInProgress(
      String applicationId, String ownerUserId, Instant dueAt, String actorId, Instant now);

  int insertPathChange(
      String id,
      String applicationId,
      String type,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now);

  /** 更新阶段。 */
  int updateStage(String id, String stage, long version, String actorId, Instant now);

  /** 更新流程结果。 */
  int updateOutcome(
      String id,
      String stage,
      String outcome,
      Instant terminalAt,
      Integer activeSlot,
      long version,
      String actorId,
      Instant now);

  /** 新增协作人变更。 */
  int insertCollaboratorChange(
      String id,
      String applicationId,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now);

  /** 更新负责人。 */
  int updateOwner(
      String id,
      String ownerUserId,
      String departmentId,
      long version,
      String actorId,
      Instant now);

  /** 查询工作条目列表。 */
  List<WorkItemRow> findWorkItems(
      String actorId, boolean scopeAll, Set<String> managedUserIds, int limit);

  List<String> findMyTaskCandidateIds(String actorId, List<String> candidateIds, String jdId);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
