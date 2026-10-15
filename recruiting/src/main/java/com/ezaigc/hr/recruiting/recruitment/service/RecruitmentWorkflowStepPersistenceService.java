package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import java.time.Instant;
import java.util.List;

/** WorkflowStep 实体的持久化服务。 */
public interface RecruitmentWorkflowStepPersistenceService extends IService<WorkflowStep> {

  /** 批量新增 Application 的完整流程步骤列表。 */
  boolean insertSteps(List<WorkflowStep> steps);

  /** 查询步骤列表。 */
  List<WorkflowStep> findSteps(String applicationId);

  /** 批量查询一页 Application 的活动步骤，按流程顺序排列。 */
  List<WorkflowStep> findActiveSteps(List<String> applicationIds);

  List<WorkflowStep> lockSteps(String applicationId);

  WorkflowStep lockStep(String applicationId, String id);

  /** 只为活动且尚未绑定的步骤写入推荐，保留所有已完成事实。 */
  int bindClientSubmission(String id, String submissionId, long version, String actorId, Instant now);

  /** 查询步骤。 */
  WorkflowStep findStep(String applicationId, String id);

  int selectStep(
      String id, String status, String ownerUserId, Instant dueAt, String actorId, Instant now);

  /** 分组设置未开始步骤的选择状态；不覆盖任何已开始或已完成事实，返回实际行数。 */
  int updateUnstartedSelection(String applicationId, List<String> ids, boolean selected,
      String actorId, Instant now);

  /** 启动步骤。 */
  int startStep(String id, long version, String actorId, Instant now);

  /** 完成步骤。 */
  int completeStep(
      String id,
      String result,
      String reason,
      String clientSubmissionId,
      long version,
      String actorId,
      Instant now);

  /** 撤回边界客户步骤。 */
  int withdrawBoundClientStep(String id, String reason, String actorId, Instant now);

  /** 完成决定步骤。 */
  int finishDecisionStep(
      String id,
      String result,
      String reason,
      String clientSubmissionId,
      String actorId,
      Instant now);

  int activateOfferStep(
      String applicationId, String ownerUserId, Instant dueAt, String actorId, Instant now);

  int finishOfferStep(
      String applicationId, String status, String reason, String actorId, Instant now);

  int closeUnreachedSteps(String applicationId, String actorId, Instant now);

  /** 终止 Application 时撤回活动普通步骤，保留已完成事实，返回实际修改行数。 */
  int withdrawActiveSteps(String applicationId, String reason, String actorId, Instant now);

  int skipStep(String id, String reason, long version, String actorId, Instant now);
}
