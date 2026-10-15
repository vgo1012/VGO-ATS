package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.IdentityAuditEvent;
import com.ezaigc.hr.identity.mapper.IdentityAuditEventMapper;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** IdentityAuditEvent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class IdentityAuditEventPersistenceServiceImpl
    extends ServiceImpl<IdentityAuditEventMapper, IdentityAuditEvent>
    implements IdentityAuditEventPersistenceService {

  /** 新增审计。 */
  @Override
  public int insertAudit(
      String id,
      String eventType,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      String beforeSnapshot,
      String afterSnapshot,
      String traceId,
      Instant now) {
    IdentityAuditEvent event = new IdentityAuditEvent();
    event.setId(id);
    event.setEventType(eventType);
    event.setActorUserId(actorId);
    event.setTargetType(targetType);
    event.setTargetId(targetId);
    event.setReason(reason);
    event.setBeforeSnapshot(beforeSnapshot);
    event.setAfterSnapshot(afterSnapshot);
    event.setTraceId(traceId);
    event.setOccurredAt(now);
    return baseMapper.insert(event);
  }
}
