package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandAuditEventEntity;
import com.ezaigc.hr.recruiting.demand.mapper.DemandAuditEventMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** DemandAuditEventEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DemandAuditEventPersistenceServiceImpl
    extends ServiceImpl<DemandAuditEventMapper, DemandAuditEventEntity>
    implements DemandAuditEventPersistenceService {

  /** 新增审计。 */
  @Override
  public int insertAudit(
      String id,
      String eventType,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      Instant now) {
    DemandAuditEventEntity event = new DemandAuditEventEntity();
    event.setId(id);
    event.setEventType(eventType);
    event.setActorUserId(actorId);
    event.setTargetType(targetType);
    event.setTargetId(targetId);
    event.setReason(reason);
    event.setOccurredAt(now);
    return baseMapper.insert(event);
  }
}
