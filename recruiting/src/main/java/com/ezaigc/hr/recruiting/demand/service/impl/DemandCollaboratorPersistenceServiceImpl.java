package com.ezaigc.hr.recruiting.demand.service.impl;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.IDENTIFIER_LENGTH;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_COLLABORATORS_PER_DEMAND;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandCollaboratorEntity;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCollaboratorMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** DemandCollaboratorEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DemandCollaboratorPersistenceServiceImpl
    extends ServiceImpl<DemandCollaboratorMapper, DemandCollaboratorEntity>
    implements DemandCollaboratorPersistenceService {

  @Override
  public List<DemandCollaboratorEntity> findForDemands(List<String> demandIds) {
    return demandIds.isEmpty()
        ? List.of()
        : baseMapper.selectList(
            Wrappers.lambdaQuery(DemandCollaboratorEntity.class)
                .in(DemandCollaboratorEntity::getDemandId, demandIds));
  }

  @Override
  public List<String> findCollaboratorIds(String demandId) {
    return baseMapper
        .selectList(
            Wrappers.lambdaQuery(DemandCollaboratorEntity.class)
                .select(DemandCollaboratorEntity::getUserId)
                .eq(DemandCollaboratorEntity::getDemandId, demandId)
                .orderByAsc(DemandCollaboratorEntity::getUserId))
        .stream()
        .map(DemandCollaboratorEntity::getUserId)
        .toList();
  }

  @Override
  public boolean insertCollaborators(
      String demandId, List<String> userIds, String actorId, Instant now) {
    if (invalidIdentifier(demandId)
        || userIds == null
        || userIds.size() > MAX_COLLABORATORS_PER_DEMAND
        || userIds.stream().anyMatch(DemandCollaboratorPersistenceServiceImpl::invalidIdentifier)
        || invalidIdentifier(actorId)
        || now == null) {
      throw new IllegalArgumentException("demand collaborator batch is invalid");
    }
    if (userIds.isEmpty()) {
      return true;
    }
    List<DemandCollaboratorEntity> collaborators =
        userIds.stream().map(userId -> collaborator(demandId, userId, actorId, now)).toList();
    return saveBatch(collaborators, MAX_COLLABORATORS_PER_DEMAND);
  }

  /** 删除协作人列表。 */
  @Override
  public int deleteCollaborators(String demandId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(DemandCollaboratorEntity.class)
            .eq(DemandCollaboratorEntity::getDemandId, demandId));
  }

  private static boolean invalidIdentifier(String value) {
    return value == null || value.isBlank() || value.length() != IDENTIFIER_LENGTH;
  }

  private static DemandCollaboratorEntity collaborator(
      String demandId, String userId, String actorId, Instant now) {
    DemandCollaboratorEntity collaborator = new DemandCollaboratorEntity();
    collaborator.setDemandId(demandId);
    collaborator.setUserId(userId);
    collaborator.setAddedByUserId(actorId);
    collaborator.setAddedAt(now);
    return collaborator;
  }
}
