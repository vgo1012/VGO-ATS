package com.ezaigc.hr.recruiting.recruitment.service.impl;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.IDENTIFIER_LENGTH;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStepStatus;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.recruitment.mapper.WorkflowStepMapper;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** WorkflowStep 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class RecruitmentWorkflowStepPersistenceServiceImpl
    extends ServiceImpl<WorkflowStepMapper, WorkflowStep>
    implements RecruitmentWorkflowStepPersistenceService {

  @Override
  public boolean insertSteps(List<WorkflowStep> steps) {
    validateBatch(steps);
    return saveBatch(steps, MAX_WORKFLOW_STEP_COUNT);
  }

  /** 查询步骤列表。 */
  @Override
  public List<WorkflowStep> findSteps(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(WorkflowStep.class)
            .eq(WorkflowStep::getApplicationId, applicationId)
            .orderByAsc(WorkflowStep::getSequenceNo, WorkflowStep::getId));
  }

  @Override
  public List<WorkflowStep> findActiveSteps(List<String> applicationIds) {
    if (applicationIds == null || applicationIds.isEmpty()) {
      return List.of();
    }
    return this.list(
        Wrappers.lambdaQuery(WorkflowStep.class)
            .in(WorkflowStep::getApplicationId, applicationIds)
            .in(WorkflowStep::getStatus, ApplicationStepStatus.PENDING.getCode(),
                ApplicationStepStatus.IN_PROGRESS.getCode())
            .orderByAsc(WorkflowStep::getApplicationId, WorkflowStep::getSequenceNo,
                WorkflowStep::getId));
  }

  /** 查询步骤。 */
  @Override
  public WorkflowStep findStep(String applicationId, String id) {
    return this.getOne(
        Wrappers.lambdaQuery(WorkflowStep.class)
            .eq(WorkflowStep::getApplicationId, applicationId)
            .eq(WorkflowStep::getId, id));
  }

  @Override
  public List<WorkflowStep> lockSteps(String applicationId) {
    return list(Wrappers.lambdaQuery(WorkflowStep.class)
        .eq(WorkflowStep::getApplicationId, applicationId)
        .orderByAsc(WorkflowStep::getSequenceNo, WorkflowStep::getId)
        .last("FOR UPDATE"));
  }

  @Override
  public WorkflowStep lockStep(String applicationId, String id) {
    return getOne(Wrappers.lambdaQuery(WorkflowStep.class)
        .eq(WorkflowStep::getApplicationId, applicationId)
        .eq(WorkflowStep::getId, id)
        .last("FOR UPDATE"));
  }

  @Override
  public int bindClientSubmission(String id, String submissionId, long version, String actorId, Instant now) {
    return baseMapper.update(null, Wrappers.lambdaUpdate(WorkflowStep.class)
        .eq(WorkflowStep::getId, id)
        .in(WorkflowStep::getStatus, ApplicationStepStatus.PENDING.getCode(), ApplicationStepStatus.IN_PROGRESS.getCode())
        .isNull(WorkflowStep::getClientSubmissionId)
        .eq(WorkflowStep::getVersion, version)
        .set(WorkflowStep::getClientSubmissionId, submissionId)
        .setIncrBy(WorkflowStep::getVersion, 1)
        .set(WorkflowStep::getUpdateId, actorId)
        .set(WorkflowStep::getUpdateTime, now));
  }

  @Override
  public int selectStep(
      String id, String status, String ownerUserId, Instant dueAt, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getId, id)
                .eq(WorkflowStep::getStatus, ApplicationStepStatus.AVAILABLE.getCode())
                .set(WorkflowStep::getStatus, status)
                .set(ApplicationStepStatus.IN_PROGRESS.matches(status), WorkflowStep::getStartedAt, now)
                .set(WorkflowStep::getOwnerUserId, ownerUserId)
                .set(WorkflowStep::getDueAt, dueAt)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  @Override
  public int updateUnstartedSelection(
      String applicationId, List<String> ids, boolean selected, String actorId, Instant now) {
    if (invalidIdentifier(applicationId) || invalidIdentifier(actorId) || now == null
        || ids == null || ids.size() > MAX_WORKFLOW_STEP_COUNT
        || ids.stream().anyMatch(RecruitmentWorkflowStepPersistenceServiceImpl::invalidIdentifier)
        || new HashSet<>(ids).size() != ids.size()) {
      throw new IllegalArgumentException("application path selection batch is invalid");
    }
    // 空集合必须短路，防止 ORM 忽略 IN 条件后扩大更新范围。
    if (ids.isEmpty()) {
      return 0;
    }
    return baseMapper.update(null,
        Wrappers.lambdaUpdate(WorkflowStep.class)
            .eq(WorkflowStep::getApplicationId, applicationId)
            .in(WorkflowStep::getId, ids)
            .in(WorkflowStep::getStatus, ApplicationStepStatus.AVAILABLE.getCode(),
                ApplicationStepStatus.NOT_SELECTED.getCode(), ApplicationStepStatus.PENDING.getCode())
            .isNull(WorkflowStep::getStartedAt)
            .isNull(WorkflowStep::getCompletedAt)
            .set(WorkflowStep::getStatus, selected ? ApplicationStepStatus.AVAILABLE.getCode()
                : ApplicationStepStatus.NOT_SELECTED.getCode())
            .set(WorkflowStep::getOwnerUserId, null)
            .set(WorkflowStep::getDueAt, null)
            .setIncrBy(WorkflowStep::getVersion, 1)
            .set(WorkflowStep::getUpdateId, actorId)
            .set(WorkflowStep::getUpdateTime, now));
  }

  /** 启动步骤。 */
  @Override
  public int startStep(String id, long version, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getId, id)
                .eq(WorkflowStep::getStatus, ApplicationStepStatus.PENDING.getCode())
                .eq(WorkflowStep::getVersion, version)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getStatus, ApplicationStepStatus.IN_PROGRESS.getCode())
                .set(WorkflowStep::getStartedAt, now)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  /** 完成步骤。 */
  @Override
  public int completeStep(
      String id,
      String result,
      String reason,
      String clientSubmissionId,
      long version,
      String actorId,
      Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getId, id)
                .in(
                    WorkflowStep::getStatus,
                    ApplicationStepStatus.PENDING.getCode(),
                    ApplicationStepStatus.IN_PROGRESS.getCode())
                .eq(WorkflowStep::getVersion, version)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getStatus, result)
                .set(WorkflowStep::getResultReason, reason)
                .set(WorkflowStep::getCompletedAt, now)
                .set(WorkflowStep::getClientSubmissionId, clientSubmissionId)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  /** 撤回边界客户步骤。 */
  @Override
  public int withdrawBoundClientStep(String id, String reason, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getId, id)
                .in(
                    WorkflowStep::getStatus,
                    ApplicationStepStatus.PENDING.getCode(),
                    ApplicationStepStatus.IN_PROGRESS.getCode())
                .set(WorkflowStep::getStatus, ApplicationStepStatus.WITHDRAWN.getCode())
                .set(WorkflowStep::getResultReason, reason)
                .set(WorkflowStep::getCompletedAt, now)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  /** 完成决定步骤。 */
  @Override
  public int finishDecisionStep(
      String id,
      String result,
      String reason,
      String clientSubmissionId,
      String actorId,
      Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getId, id)
                .eq(WorkflowStep::getStepCode, WorkflowStepCode.DECISION.getCode())
                .in(
                    WorkflowStep::getStatus,
                    ApplicationStepStatus.PENDING.getCode(),
                    ApplicationStepStatus.IN_PROGRESS.getCode())
                .set(WorkflowStep::getStatus, result)
                .set(WorkflowStep::getResultReason, reason)
                .set(WorkflowStep::getCompletedAt, now)
                .set(WorkflowStep::getClientSubmissionId, clientSubmissionId)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  @Override
  public int activateOfferStep(
      String applicationId, String ownerUserId, Instant dueAt, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getApplicationId, applicationId)
                .eq(WorkflowStep::getStepCode, WorkflowStepCode.OFFER.getCode())
                .in(WorkflowStep::getStatus,
                    ApplicationStepStatus.AVAILABLE.getCode(),
                    ApplicationStepStatus.NOT_SELECTED.getCode(),
                    ApplicationStepStatus.FAILED.getCode(),
                    ApplicationStepStatus.WITHDRAWN.getCode())
                .set(WorkflowStep::getStatus, ApplicationStepStatus.IN_PROGRESS.getCode())
                .set(WorkflowStep::getOwnerUserId, ownerUserId)
                .set(WorkflowStep::getDueAt, dueAt)
                .set(WorkflowStep::getStartedAt, now)
                .set(WorkflowStep::getCompletedAt, null)
                .set(WorkflowStep::getResultReason, null)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  @Override
  public int finishOfferStep(
      String applicationId, String status, String reason, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getApplicationId, applicationId)
                .eq(WorkflowStep::getStepCode, WorkflowStepCode.OFFER.getCode())
                .in(
                    WorkflowStep::getStatus,
                    ApplicationStepStatus.PENDING.getCode(),
                    ApplicationStepStatus.IN_PROGRESS.getCode(),
                    ApplicationStepStatus.AVAILABLE.getCode())
                .set(WorkflowStep::getStatus, status)
                .set(WorkflowStep::getResultReason, reason)
                .set(WorkflowStep::getCompletedAt, now)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  @Override
  public int closeUnreachedSteps(String applicationId, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getApplicationId, applicationId)
                .eq(WorkflowStep::getStatus, ApplicationStepStatus.AVAILABLE.getCode())
                .set(WorkflowStep::getStatus, ApplicationStepStatus.NOT_SELECTED.getCode())
                .set(WorkflowStep::getOwnerUserId, null)
                .set(WorkflowStep::getDueAt, null)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  @Override
  public int withdrawActiveSteps(String applicationId, String reason, String actorId, Instant now) {
    return baseMapper.update(null,
        Wrappers.lambdaUpdate(WorkflowStep.class)
            .eq(WorkflowStep::getApplicationId, applicationId)
            .in(WorkflowStep::getStatus, ApplicationStepStatus.PENDING.getCode(),
                ApplicationStepStatus.IN_PROGRESS.getCode())
            .notIn(WorkflowStep::getStepCode, WorkflowStepCode.DECISION.getCode(),
                WorkflowStepCode.OFFER.getCode())
            .set(WorkflowStep::getStatus, ApplicationStepStatus.WITHDRAWN.getCode())
            .set(WorkflowStep::getResultReason, reason)
            .set(WorkflowStep::getCompletedAt, now)
            .setIncrBy(WorkflowStep::getVersion, 1)
            .set(WorkflowStep::getUpdateId, actorId)
            .set(WorkflowStep::getUpdateTime, now));
  }

  @Override
  public int skipStep(String id, String reason, long version, String actorId, Instant now) {
    return affected(
        this.update(
            Wrappers.lambdaUpdate(WorkflowStep.class)
                .eq(WorkflowStep::getId, id)
                .in(
                    WorkflowStep::getStatus,
                    ApplicationStepStatus.PENDING.getCode(),
                    ApplicationStepStatus.IN_PROGRESS.getCode())
                .eq(WorkflowStep::getVersion, version)
                .setIncrBy(WorkflowStep::getVersion, 1)
                .set(WorkflowStep::getStatus, ApplicationStepStatus.SKIPPED.getCode())
                .set(WorkflowStep::getResultReason, reason)
                .set(WorkflowStep::getCompletedAt, now)
                .set(WorkflowStep::getUpdateId, actorId)
                .set(WorkflowStep::getUpdateTime, now)));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }

  private static void validateBatch(List<WorkflowStep> steps) {
    if (steps == null || steps.isEmpty() || steps.size() > MAX_WORKFLOW_STEP_COUNT) {
      throw new IllegalArgumentException("application workflow step batch is invalid");
    }
    WorkflowStep first = steps.getFirst();
    String applicationId = first == null ? null : first.getApplicationId();
    String templateId = first == null ? null : first.getTemplateVersionId();
    if (invalidIdentifier(applicationId) || invalidIdentifier(templateId)) {
      throw new IllegalArgumentException("application workflow step batch is invalid");
    }
    Set<String> ids = new HashSet<>(steps.size());
    Set<String> definitionIds = new HashSet<>(steps.size());
    Set<String> codes = new HashSet<>(steps.size());
    Set<Integer> sequences = new HashSet<>(steps.size());
    for (WorkflowStep step : steps) {
      if (step == null
          || invalidIdentifier(step.getId())
          || !applicationId.equals(step.getApplicationId())
          || !templateId.equals(step.getTemplateVersionId())
          || invalidIdentifier(step.getStepDefinitionId())
          || step.getStepCode() == null
          || !WorkflowStepCode.supports(step.getStepCode())
          || step.getSequenceNo() < 1
          || step.getSequenceNo() > steps.size()
          || !ApplicationStepStatus.AVAILABLE.matches(step.getStatus())
          || step.getVersion() != 0L
          || step.getCreateTime() == null
          || step.getUpdateTime() == null
          || !step.getCreateTime().equals(step.getUpdateTime())
          || !ids.add(step.getId())
          || !definitionIds.add(step.getStepDefinitionId())
          || !codes.add(step.getStepCode())
          || !sequences.add(step.getSequenceNo())) {
        throw new IllegalArgumentException("application workflow step batch is invalid");
      }
    }
  }

  private static boolean invalidIdentifier(String value) {
    return value == null || value.isBlank() || value.length() != IDENTIFIER_LENGTH;
  }

}
