package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.enums.DemandStatus;
import com.ezaigc.hr.recruiting.demand.mapper.DemandEntityMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** DemandEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DemandEntityPersistenceServiceImpl
    extends ServiceImpl<DemandEntityMapper, DemandEntity>
    implements DemandEntityPersistenceService {

  /** 查询招聘需求。 */
  @Override
  public DemandEntity findDemand(String id) {
    return baseMapper.selectById(id);
  }

  /** 统计招聘需求编码。 */
  @Override
  public int countDemandCode(String code) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(DemandEntity.class).eq(DemandEntity::getCode, code)));
  }

  /** 新增招聘需求。 */
  @Override
  public int insertDemand(
      String id,
      String code,
      String title,
      String clientId,
      String projectId,
      String positionId,
      String recruitmentType,
      int headcount,
      String description,
      String ownerUserId,
      String ownerDepartmentId,
      String actorId,
      Instant now) {
    DemandEntity demand = new DemandEntity();
    demand.setId(id);
    demand.setCode(code);
    demand.setTitle(title);
    demand.setClientId(clientId);
    demand.setProjectId(projectId);
    demand.setPositionId(positionId);
    demand.setRecruitmentType(recruitmentType);
    demand.setHeadcount(headcount);
    demand.setDescription(description);
    demand.setStatus(DemandStatus.DRAFT.getCode());
    demand.setOwnerUserId(ownerUserId);
    demand.setOwnerDepartmentId(ownerDepartmentId);
    demand.setVersion(0L);
    demand.setCreateId(actorId);
    demand.setCreateTime(now);
    demand.setUpdateId(actorId);
    demand.setUpdateTime(now);
    return baseMapper.insert(demand);
  }

  /** 更新招聘需求。 */
  @Override
  public int updateDemand(
      String id,
      String title,
      String clientId,
      String projectId,
      String positionId,
      String recruitmentType,
      int headcount,
      String description,
      String ownerUserId,
      String ownerDepartmentId,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(DemandEntity.class)
            .eq(DemandEntity::getId, id)
            .eq(DemandEntity::getVersion, version)
            .setIncrBy(DemandEntity::getVersion, 1)
            .ne(DemandEntity::getStatus, DemandStatus.CLOSED.getCode())
            .set(DemandEntity::getTitle, title)
            .set(DemandEntity::getClientId, clientId)
            .set(DemandEntity::getProjectId, projectId)
            .set(DemandEntity::getPositionId, positionId)
            .set(DemandEntity::getRecruitmentType, recruitmentType)
            .set(DemandEntity::getHeadcount, headcount)
            .set(DemandEntity::getDescription, description)
            .set(DemandEntity::getOwnerUserId, ownerUserId)
            .set(DemandEntity::getOwnerDepartmentId, ownerDepartmentId)
            .set(DemandEntity::getUpdateId, actorId)
            .set(DemandEntity::getUpdateTime, now));
  }

  @Override
  public int transitionDemand(
      String id, String fromStatus, String toStatus, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(DemandEntity.class)
            .eq(DemandEntity::getId, id)
            .eq(DemandEntity::getVersion, version)
            .setIncrBy(DemandEntity::getVersion, 1)
            .eq(DemandEntity::getStatus, fromStatus)
            .set(DemandEntity::getStatus, toStatus)
            .set(DemandEntity::getUpdateId, actorId)
            .set(DemandEntity::getUpdateTime, now));
  }

  /** 删除招聘需求。 */
  @Override
  public int deleteDemand(String id, long version) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(DemandEntity.class)
            .eq(DemandEntity::getId, id)
            .eq(DemandEntity::getStatus, DemandStatus.DRAFT.getCode())
            .eq(DemandEntity::getVersion, version));
  }
}
