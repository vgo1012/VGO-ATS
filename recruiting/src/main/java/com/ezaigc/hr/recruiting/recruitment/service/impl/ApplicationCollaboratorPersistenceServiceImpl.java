package com.ezaigc.hr.recruiting.recruitment.service.impl;

import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_COLLABORATORS_PER_APPLICATION;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationCollaboratorMapper;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import java.util.Collection;

/** ApplicationCollaborator 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ApplicationCollaboratorPersistenceServiceImpl
    extends ServiceImpl<ApplicationCollaboratorMapper, ApplicationCollaborator>
    implements ApplicationCollaboratorPersistenceService {

  @Override
  public boolean insertCollaborators(
      String applicationId, List<String> userIds, String actorId, Instant now) {
    if (applicationId == null
        || applicationId.isBlank()
        || userIds == null
        || userIds.size() > MAX_COLLABORATORS_PER_APPLICATION
        || userIds.stream().anyMatch(userId -> userId == null || userId.isBlank())
        || actorId == null
        || actorId.isBlank()
        || now == null) {
      throw new IllegalArgumentException("application collaborator batch is invalid");
    }
    if (userIds.isEmpty()) {
      return true;
    }
    List<ApplicationCollaborator> collaborators =
        userIds.stream().map(userId -> collaborator(applicationId, userId, actorId, now)).toList();
    return saveBatch(collaborators, MAX_COLLABORATORS_PER_APPLICATION);
  }

  /** 删除协作人列表。 */
  @Override
  public int deleteCollaborators(String applicationId) {
    return affected(
        this.remove(
            Wrappers.lambdaQuery(ApplicationCollaborator.class)
                .eq(ApplicationCollaborator::getApplicationId, applicationId)));
  }

  /** 查询协作人列表。 */
  @Override
  public List<String> findCollaborators(String applicationId) {
    return this.list(
            Wrappers.lambdaQuery(ApplicationCollaborator.class)
                .eq(ApplicationCollaborator::getApplicationId, applicationId)
                .orderByAsc(ApplicationCollaborator::getUserId))
        .stream()
        .map(ApplicationCollaborator::getUserId)
        .toList();
  }

  /** Application 锁内读取已提交协作人事实，避免沿用 HC 查询建立的旧快照。 */
  @Override
  public List<String> findCollaboratorsForUpdate(String applicationId) {
    return this.list(
            Wrappers.lambdaQuery(ApplicationCollaborator.class)
                .eq(ApplicationCollaborator::getApplicationId, applicationId)
                .orderByAsc(ApplicationCollaborator::getUserId)
                .last("FOR UPDATE"))
        .stream()
        .map(ApplicationCollaborator::getUserId)
        .toList();
  }

  @Override
  public List<ApplicationCollaborator> findCollaboratorsByApplicationIds(
      Collection<String> applicationIds) {
    if (applicationIds == null || applicationIds.isEmpty()) {
      return List.of();
    }
    return this.list(
        Wrappers.lambdaQuery(ApplicationCollaborator.class)
            .in(ApplicationCollaborator::getApplicationId, applicationIds)
            .orderByAsc(
                ApplicationCollaborator::getApplicationId, ApplicationCollaborator::getUserId));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }

  private static ApplicationCollaborator collaborator(
      String applicationId, String userId, String actorId, Instant now) {
    ApplicationCollaborator collaborator = new ApplicationCollaborator();
    collaborator.setApplicationId(applicationId);
    collaborator.setUserId(userId);
    collaborator.setAddedByUserId(actorId);
    collaborator.setCreateTime(now);
    return collaborator;
  }
}
