package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.OwnershipHistoryEntity;
import com.ezaigc.hr.recruiting.demand.mapper.OwnershipHistoryMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandOwnershipHistoryPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** OwnershipHistoryEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DemandOwnershipHistoryPersistenceServiceImpl
    extends ServiceImpl<OwnershipHistoryMapper, OwnershipHistoryEntity>
    implements DemandOwnershipHistoryPersistenceService {

  /** 新增归属历史。 */
  @Override
  public int insertOwnershipHistory(
      String id,
      String aggregateType,
      String aggregateId,
      String fromOwnerUserId,
      String toOwnerUserId,
      String fromDepartmentId,
      String toDepartmentId,
      String reason,
      String actorId,
      Instant now) {
    OwnershipHistoryEntity history = new OwnershipHistoryEntity();
    history.setId(id);
    history.setAggregateType(aggregateType);
    history.setAggregateId(aggregateId);
    history.setFromOwnerUserId(fromOwnerUserId);
    history.setToOwnerUserId(toOwnerUserId);
    history.setFromDepartmentId(fromDepartmentId);
    history.setToDepartmentId(toDepartmentId);
    history.setReason(reason);
    history.setActorUserId(actorId);
    history.setOccurredAt(now);
    return baseMapper.insert(history);
  }
}
