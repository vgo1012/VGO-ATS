package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import com.ezaigc.hr.recruiting.demand.enums.DemandActivationStatus;
import com.ezaigc.hr.recruiting.demand.mapper.PositionMapper;
import com.ezaigc.hr.recruiting.demand.service.PositionPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** PositionEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class PositionPersistenceServiceImpl extends ServiceImpl<PositionMapper, PositionEntity>
    implements PositionPersistenceService {

  /** 查询职位。 */
  @Override
  public PositionEntity findPosition(String id) {
    return baseMapper.selectById(id);
  }

  /** 统计职位编码。 */
  @Override
  public int countPositionCode(String clientId, String code) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(PositionEntity.class)
                .eq(PositionEntity::getClientId, clientId)
                .eq(PositionEntity::getCode, code)));
  }

  /** 新增职位。 */
  @Override
  public int insertPosition(
      String id,
      String clientId,
      String projectId,
      String code,
      String name,
      String actorId,
      Instant now) {
    PositionEntity position = new PositionEntity();
    position.setId(id);
    position.setClientId(clientId);
    position.setProjectId(projectId);
    position.setCode(code);
    position.setName(name);
    position.setStatus(DemandActivationStatus.ACTIVE.getCode());
    position.setVersion(0L);
    position.setCreateId(actorId);
    position.setCreateTime(now);
    position.setUpdateId(actorId);
    position.setUpdateTime(now);
    return baseMapper.insert(position);
  }

  /** 更新职位。 */
  @Override
  public int updatePosition(
      String id, String projectId, String name, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(PositionEntity.class)
            .eq(PositionEntity::getId, id)
            .eq(PositionEntity::getVersion, version)
            .setIncrBy(PositionEntity::getVersion, 1)
            .eq(PositionEntity::getStatus, DemandActivationStatus.ACTIVE.getCode())
            .set(PositionEntity::getProjectId, projectId)
            .set(PositionEntity::getName, name)
            .set(PositionEntity::getUpdateId, actorId)
            .set(PositionEntity::getUpdateTime, now));
  }

  /** 停用职位。 */
  @Override
  public int disablePosition(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(PositionEntity.class)
            .eq(PositionEntity::getId, id)
            .eq(PositionEntity::getVersion, version)
            .setIncrBy(PositionEntity::getVersion, 1)
            .eq(PositionEntity::getStatus, DemandActivationStatus.ACTIVE.getCode())
            .set(PositionEntity::getStatus, DemandActivationStatus.DISABLED.getCode())
            .set(PositionEntity::getUpdateId, actorId)
            .set(PositionEntity::getUpdateTime, now));
  }
}
