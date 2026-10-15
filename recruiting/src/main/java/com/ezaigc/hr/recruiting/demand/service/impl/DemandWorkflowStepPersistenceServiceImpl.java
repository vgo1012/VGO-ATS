package com.ezaigc.hr.recruiting.demand.service.impl;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.IDENTIFIER_LENGTH;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.demand.mapper.WorkflowStepMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandWorkflowStepPersistenceService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** WorkflowStepEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DemandWorkflowStepPersistenceServiceImpl
    extends ServiceImpl<WorkflowStepMapper, WorkflowStepEntity>
    implements DemandWorkflowStepPersistenceService {

  @Override
  public boolean insertWorkflowSteps(List<WorkflowStepEntity> steps) {
    validateBatch(steps);
    return saveBatch(steps, MAX_WORKFLOW_STEP_COUNT);
  }

  /** 查询流程步骤列表。 */
  @Override
  public List<WorkflowStepEntity> findWorkflowSteps(String templateId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(WorkflowStepEntity.class)
            .eq(WorkflowStepEntity::getTemplateVersionId, templateId)
            .orderByAsc(WorkflowStepEntity::getSequenceNo, WorkflowStepEntity::getId));
  }

  @Override
  public List<WorkflowStepEntity> findWorkflowStepsByTemplateIds(Collection<String> templateIds) {
    if (templateIds == null || templateIds.isEmpty()) {
      return List.of();
    }
    return baseMapper.selectList(
        Wrappers.lambdaQuery(WorkflowStepEntity.class)
            .in(WorkflowStepEntity::getTemplateVersionId, templateIds)
            .orderByAsc(
                WorkflowStepEntity::getTemplateVersionId,
                WorkflowStepEntity::getSequenceNo,
                WorkflowStepEntity::getId));
  }

  /** 删除流程步骤列表。 */
  @Override
  public int deleteWorkflowSteps(String templateId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(WorkflowStepEntity.class)
            .eq(WorkflowStepEntity::getTemplateVersionId, templateId));
  }

  private static void validateBatch(List<WorkflowStepEntity> steps) {
    if (steps == null || steps.isEmpty() || steps.size() > MAX_WORKFLOW_STEP_COUNT) {
      throw new IllegalArgumentException("workflow step batch is invalid");
    }
    String templateId = steps.getFirst() == null ? null : steps.getFirst().getTemplateVersionId();
    if (invalidIdentifier(templateId)) {
      throw new IllegalArgumentException("workflow step batch is invalid");
    }
    Set<String> ids = new HashSet<>(steps.size());
    Set<String> codes = new HashSet<>(steps.size());
    Set<Integer> sequences = new HashSet<>(steps.size());
    for (WorkflowStepEntity step : steps) {
      if (step == null
          || invalidIdentifier(step.getId())
          || !templateId.equals(step.getTemplateVersionId())
          || step.getStepCode() == null
          || !WorkflowStepCode.supports(step.getStepCode())
          || step.getSequenceNo() == null
          || step.getSequenceNo() < 1
          || step.getSequenceNo() > steps.size()
          || step.getCandidateOptional() == null
          || step.getCreateTime() == null
          || !ids.add(step.getId())
          || !codes.add(step.getStepCode())
          || !sequences.add(step.getSequenceNo())) {
        throw new IllegalArgumentException("workflow step batch is invalid");
      }
    }
  }

  private static boolean invalidIdentifier(String value) {
    return value == null || value.isBlank() || value.length() != IDENTIFIER_LENGTH;
  }

}
