package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchingAuditEvent;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingAuditEventMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** MatchingAuditEvent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MatchingAuditEventPersistenceServiceImpl
    extends ServiceImpl<MatchingAuditEventMapper, MatchingAuditEvent>
    implements MatchingAuditEventPersistenceService {

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
    MatchingAuditEvent event = new MatchingAuditEvent();
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
