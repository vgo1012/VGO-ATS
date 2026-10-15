package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.OwnershipHistoryEntity;
import java.time.Instant;

/** OwnershipHistoryEntity 实体的持久化服务。 */
public interface DemandOwnershipHistoryPersistenceService extends IService<OwnershipHistoryEntity> {

  /** 新增归属历史。 */
  int insertOwnershipHistory(
      String id,
      String aggregateType,
      String aggregateId,
      String fromOwnerUserId,
      String toOwnerUserId,
      String fromDepartmentId,
      String toDepartmentId,
      String reason,
      String actorId,
      Instant now);
}
