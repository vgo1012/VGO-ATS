package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowTemplateStatus;
import com.ezaigc.hr.recruiting.demand.mapper.WorkflowTemplateMapper;
import com.ezaigc.hr.recruiting.demand.service.WorkflowTemplatePersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** WorkflowTemplateEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class WorkflowTemplatePersistenceServiceImpl
    extends ServiceImpl<WorkflowTemplateMapper, WorkflowTemplateEntity>
    implements WorkflowTemplatePersistenceService {

  /** 新增流程模板。 */
  @Override
  public int insertWorkflowTemplate(
      String id,
      String jdVersionId,
      int versionNo,
      String recruitmentType,
      String actorId,
      Instant now) {
    WorkflowTemplateEntity template = new WorkflowTemplateEntity();
    template.setId(id);
    template.setJdVersionId(jdVersionId);
    template.setVersionNo(versionNo);
    template.setRecruitmentType(recruitmentType);
    template.setStatus(WorkflowTemplateStatus.DRAFT.getCode());
    template.setVersion(0L);
    template.setCreateId(actorId);
    template.setCreateTime(now);
    template.setUpdateId(actorId);
    template.setUpdateTime(now);
    return baseMapper.insert(template);
  }

  /** 查询流程模板。 */
  @Override
  public WorkflowTemplateEntity findWorkflowTemplate(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询流程模板列表。 */
  @Override
  public List<WorkflowTemplateEntity> findWorkflowTemplates(
      String jdVersionId, Instant cursorTime, String cursorId, int limit) {
    var query =
        Wrappers.lambdaQuery(WorkflowTemplateEntity.class)
            .eq(WorkflowTemplateEntity::getJdVersionId, jdVersionId)
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(WorkflowTemplateEntity::getCreateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(WorkflowTemplateEntity::getCreateTime, cursorTime)
                                    .lt(WorkflowTemplateEntity::getId, cursorId)))
            .orderByDesc(WorkflowTemplateEntity::getVersionNo, WorkflowTemplateEntity::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  /** 更新流程模板。 */
  @Override
  public int updateWorkflowTemplate(
      String id, String recruitmentType, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(WorkflowTemplateEntity.class)
            .eq(WorkflowTemplateEntity::getId, id)
            .eq(WorkflowTemplateEntity::getVersion, version)
            .setIncrBy(WorkflowTemplateEntity::getVersion, 1)
            .eq(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.DRAFT.getCode())
            .set(WorkflowTemplateEntity::getRecruitmentType, recruitmentType)
            .set(WorkflowTemplateEntity::getUpdateId, actorId)
            .set(WorkflowTemplateEntity::getUpdateTime, now));
  }

  /** 统计生效流程。 */
  @Override
  public int countActiveWorkflow(String jdVersionId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(WorkflowTemplateEntity.class)
                .eq(WorkflowTemplateEntity::getJdVersionId, jdVersionId)
                .eq(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.ACTIVE.getCode())));
  }

  /** 查询生效流程。 */
  @Override
  public WorkflowTemplateEntity findActiveWorkflow(String jdVersionId) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(WorkflowTemplateEntity.class)
            .eq(WorkflowTemplateEntity::getJdVersionId, jdVersionId)
            .eq(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.ACTIVE.getCode()));
  }

  @Override
  public int supersedeActiveWorkflow(String jdVersionId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(WorkflowTemplateEntity.class)
            .eq(WorkflowTemplateEntity::getJdVersionId, jdVersionId)
            .eq(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.ACTIVE.getCode())
            .set(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.SUPERSEDED.getCode())
            .set(WorkflowTemplateEntity::getActiveSlot, null)
            .setIncrBy(WorkflowTemplateEntity::getVersion, 1)
            .set(WorkflowTemplateEntity::getUpdateId, actorId)
            .set(WorkflowTemplateEntity::getUpdateTime, now));
  }

  /** 激活流程模板。 */
  @Override
  public int activateWorkflowTemplate(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(WorkflowTemplateEntity.class)
            .eq(WorkflowTemplateEntity::getId, id)
            .eq(WorkflowTemplateEntity::getVersion, version)
            .setIncrBy(WorkflowTemplateEntity::getVersion, 1)
            .eq(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.DRAFT.getCode())
            .set(WorkflowTemplateEntity::getStatus, WorkflowTemplateStatus.ACTIVE.getCode())
            .set(WorkflowTemplateEntity::getActiveSlot, 1)
            .set(WorkflowTemplateEntity::getActivatedByUserId, actorId)
            .set(WorkflowTemplateEntity::getActivatedAt, now)
            .set(WorkflowTemplateEntity::getUpdateId, actorId)
            .set(WorkflowTemplateEntity::getUpdateTime, now));
  }
}
