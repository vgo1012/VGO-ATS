package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import java.time.Instant;
import java.util.List;

/** WorkflowTemplateEntity 实体的持久化服务。 */
public interface WorkflowTemplatePersistenceService extends IService<WorkflowTemplateEntity> {

  /** 新增流程模板。 */
  int insertWorkflowTemplate(
      String id,
      String jdVersionId,
      int versionNo,
      String recruitmentType,
      String actorId,
      Instant now);

  /** 查询流程模板。 */
  WorkflowTemplateEntity findWorkflowTemplate(String id);

  /** 查询流程模板列表。 */
  List<WorkflowTemplateEntity> findWorkflowTemplates(
      String jdVersionId, Instant cursorTime, String cursorId, int limit);

  /** 更新流程模板。 */
  int updateWorkflowTemplate(
      String id, String recruitmentType, long version, String actorId, Instant now);

  /** 统计生效流程。 */
  int countActiveWorkflow(String jdVersionId);

  /** 查询生效流程。 */
  WorkflowTemplateEntity findActiveWorkflow(String jdVersionId);

  int supersedeActiveWorkflow(String jdVersionId, String actorId, Instant now);

  /** 激活流程模板。 */
  int activateWorkflowTemplate(String id, long version, String actorId, Instant now);
}
