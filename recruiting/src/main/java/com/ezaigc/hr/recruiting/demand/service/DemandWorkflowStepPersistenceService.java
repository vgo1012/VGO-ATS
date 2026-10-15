package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import java.util.Collection;
import java.util.List;

/** WorkflowStepEntity 实体的持久化服务。 */
public interface DemandWorkflowStepPersistenceService extends IService<WorkflowStepEntity> {

  /** 批量新增完整流程步骤列表。 */
  boolean insertWorkflowSteps(List<WorkflowStepEntity> steps);

  /** 查询流程步骤列表。 */
  List<WorkflowStepEntity> findWorkflowSteps(String templateId);

  /** 按模板版本批量查询流程步骤列表。 */
  List<WorkflowStepEntity> findWorkflowStepsByTemplateIds(Collection<String> templateIds);

  /** 删除流程步骤列表。 */
  int deleteWorkflowSteps(String templateId);
}
