package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import java.time.Instant;

/** PositionEntity 实体的持久化服务。 */
public interface PositionPersistenceService extends IService<PositionEntity> {

  /** 查询职位。 */
  PositionEntity findPosition(String id);

  /** 统计职位编码。 */
  int countPositionCode(String clientId, String code);

  /** 新增职位。 */
  int insertPosition(
      String id,
      String clientId,
      String projectId,
      String code,
      String name,
      String actorId,
      Instant now);

  /** 更新职位。 */
  int updatePosition(
      String id, String projectId, String name, long version, String actorId, Instant now);

  /** 停用职位。 */
  int disablePosition(String id, long version, String actorId, Instant now);
}
