package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountQuery;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItemRow;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStage;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 应聘流程持久化服务实现，负责把业务操作落实到数据库。 Thin persistence implementation; permissions, transactions, and
 * business rules stay in managers.
 */
@Service
public class ApplicationPersistenceServiceImpl extends ServiceImpl<ApplicationMapper, Application>
    implements ApplicationPersistenceService {

  @Override
  public boolean hasActiveApplicationsForJd(String jdId) {
    return baseMapper.exists(Wrappers.<Application>lambdaQuery().eq(Application::getJdId, jdId)
        .notIn(Application::getCurrentStage,
            ApplicationStage.TERMINATED.getCode(), ApplicationStage.ONBOARDED.getCode()));
  }

  @Override
  public boolean hasMergeProtectedHistory(String candidateId) {
    return baseMapper.findMergeApplication(candidateId) != null
        || baseMapper.findMergeInterview(candidateId) != null
        || baseMapper.findMergeIntention(candidateId) != null
        || baseMapper.findMergeEngagement(candidateId) != null
        || baseMapper.findMergeEngagementHistory(candidateId) != null
        || baseMapper.findMergeCollaboration(candidateId) != null
        || baseMapper.findMergeSubmission(candidateId) != null
        || baseMapper.findMergeClientRestriction(candidateId) != null
        || baseMapper.findMergeClientRestrictionProjection(candidateId) != null
        || baseMapper.findMergeJdSource(candidateId) != null;
  }
  @Override
  public List<ApplicationStageCountDTO> countStages(ApplicationStageCountQuery query) {
    if (query.getGrantedApplicationIds() != null && query.getGrantedApplicationIds().isEmpty()) {
      return List.of();
    }
    return baseMapper.countStages(query);
  }
  @Override
  public boolean hasRecruitmentHistory(String resumeVersionId) {
    return baseMapper.findRecruitmentHistoryForRouting(resumeVersionId) != null;
  }
  @Override
  public boolean hasActiveRecruitment(String resumeVersionId) {
    return baseMapper.findActiveRecruitmentForRouting(resumeVersionId) != null;
  }
  @Override
  public int enableProgressiveStage(String id, String stage, long version, String actorId, Instant now) {
    return affected(update(Wrappers.lambdaUpdate(Application.class).eq(Application::getId, id)
        .eq(Application::getVersion, version).setIncrBy(Application::getVersion, 1)
        .set(Application::isProgressiveFollowUp, true).set(Application::getCurrentStage, stage)
        .set(Application::getUpdateId, actorId).set(Application::getUpdateTime, now)));
  }

  @Override
  public Application findActiveForCandidateAndJd(String candidateId, String jdId) {
    return getOne(Wrappers.<Application>lambdaQuery().eq(Application::getCandidateId, candidateId)
        .eq(Application::getJdId, jdId).eq(Application::getActiveSlot, 1L));
  }
  @Override
  public List<Application> findForCandidates(List<String> candidateIds) {
    return candidateIds.isEmpty() ? List.of() : list(Wrappers.lambdaQuery(Application.class)
        .in(Application::getCandidateId, candidateIds));
  }

  @Override
  public List<Application> findCreatedInRange(Instant from, Instant to, String jdId,
      String jdVersionId, String afterId, int limit) {
    return baseMapper.selectPage(new Page<>(1, Math.max(1, Math.min(limit, 200)), false), Wrappers.<Application>lambdaQuery()
        .ge(Application::getCreateTime, from).lt(Application::getCreateTime, to)
        .eq(jdId != null, Application::getJdId, jdId)
        .eq(jdVersionId != null, Application::getJdVersionId, jdVersionId)
        .gt(afterId != null, Application::getId, afterId)
        .orderByAsc(Application::getId)).getRecords();
  }

  /** 新增应聘流程。 */
  @Override
  public int insertApplication(
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
      Instant now) {
    Application application = new Application();
    application.setId(id);
    application.setCandidateId(candidateId);
    application.setDemandId(demandId);
    application.setJdId(jdId);
    application.setJdVersionId(jdVersionId);
    application.setResumeVersionId(resumeVersionId);
    application.setWorkflowTemplateVersionId(workflowId);
    application.setMatchPlanVersionId(matchPlanId);
    application.setRecruitmentType(recruitmentType);
    application.setProgressiveFollowUp(true);
    application.setCurrentStage(ApplicationStage.PATH_SELECTION.getCode());
    application.setOwnerUserId(ownerUserId);
    application.setOwnerDepartmentId(departmentId);
    application.setActiveSlot(1L);
    application.setVersion(0L);
    application.setCreateId(actorId);
    application.setCreateTime(now);
    application.setUpdateId(actorId);
    application.setUpdateTime(now);
    return affected(save(application));
  }

  /** 查询应聘流程。 */
  @Override
  public Application findApplication(String id) {
    return getById(id);
  }

  @Override
  public String findJdVersionId(String id) {
    return baseMapper.findJdVersionId(id);
  }

  @Override
  public String findCandidateId(String id) {
    return baseMapper.findCandidateId(id);
  }

  /** 更新阶段。 */
  @Override
  public int updateStage(String id, String stage, long version, String actorId, Instant now) {
    return affected(
        update(
            Wrappers.lambdaUpdate(Application.class)
                .eq(Application::getId, id)
                .eq(Application::getVersion, version)
                .setIncrBy(Application::getVersion, 1)
                .set(Application::getCurrentStage, stage)
                .set(Application::getUpdateId, actorId)
                .set(Application::getUpdateTime, now)));
  }

  /** 更新流程结果。 */
  @Override
  public int updateOutcome(
      String id,
      String stage,
      String outcome,
      Instant terminalAt,
      Integer activeSlot,
      long version,
      String actorId,
      Instant now) {
    return affected(
        update(
            Wrappers.lambdaUpdate(Application.class)
                .eq(Application::getId, id)
                .eq(Application::getVersion, version)
                .setIncrBy(Application::getVersion, 1)
                .set(Application::getCurrentStage, stage)
                .set(Application::getOutcome, outcome)
                .set(Application::getTerminalAt, terminalAt)
                .set(Application::getActiveSlot, activeSlot)
                .set(Application::getUpdateId, actorId)
                .set(Application::getUpdateTime, now)));
  }

  /** 更新负责人。 */
  @Override
  public int updateOwner(
      String id,
      String ownerUserId,
      String departmentId,
      long version,
      String actorId,
      Instant now) {
    return affected(
        update(
            Wrappers.lambdaUpdate(Application.class)
                .eq(Application::getId, id)
                .eq(Application::getVersion, version)
                .setIncrBy(Application::getVersion, 1)
                .set(Application::getOwnerUserId, ownerUserId)
                .set(Application::getOwnerDepartmentId, departmentId)
                .set(Application::getUpdateId, actorId)
                .set(Application::getUpdateTime, now)));
  }

  @Override
  public Application lockApplication(String id) {
    return baseMapper.lockApplication(id);
  }

  @Override
  public List<Application> findApplications(
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
      int limit) {
    return baseMapper.findApplications(
        jdId, jdVersionId, ownerUserId, stage, workflowStepCode, lifecycle, from, to, interviewType,
        false, cursorTime, cursorId, limit);
  }

  @Override
  public List<Application> findApplications(
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
      int limit) {
    return baseMapper.findApplications(
        jdId, jdVersionId, ownerUserId, stage, workflowStepCode, lifecycle, from, to, interviewType,
        onboarding, cursorTime, cursorId, limit);
  }

  @Override
  public int markOfferInProgress(
      String applicationId, String ownerUserId, Instant dueAt, String actorId, Instant now) {
    return baseMapper.markOfferInProgress(applicationId, ownerUserId, dueAt, actorId, now);
  }

  @Override
  public int insertPathChange(
      String id,
      String applicationId,
      String type,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.insertPathChange(
        id, applicationId, type, beforeJson, afterJson, reason, version, actorId, now);
  }

  @Override
  public int insertCollaboratorChange(
      String id,
      String applicationId,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.insertCollaboratorChange(
        id, applicationId, beforeJson, afterJson, reason, version, actorId, now);
  }

  @Override
  public List<WorkItemRow> findWorkItems(
      String actorId, boolean scopeAll, Set<String> managedUserIds, int limit) {
    return baseMapper.findWorkItems(actorId, scopeAll, managedUserIds, limit);
  }

  @Override
  public List<String> findMyTaskCandidateIds(String actorId, List<String> candidateIds, String jdId) {
    return baseMapper.findMyTaskCandidateIds(actorId, candidateIds, jdId);
  }

  @Override
  public int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now) {
    return baseMapper.insertIdempotency(actorId, key, action, fingerprint, targetId, now);
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }

  private static int affected(int affectedRows) {
    return affectedRows;
  }
}
