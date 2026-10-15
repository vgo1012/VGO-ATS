package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import com.ezaigc.hr.talent.mapper.TalentProcessEventMapper;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** TalentProcessEvent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class TalentProcessEventPersistenceServiceImpl
    extends ServiceImpl<TalentProcessEventMapper, TalentProcessEvent>
    implements TalentProcessEventPersistenceService {

  @Override
  public int insertEvent(
      String id,
      String event,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      Instant now) {
    TalentProcessEvent processEvent = new TalentProcessEvent();
    processEvent.setId(id);
    processEvent.setEventType(event);
    processEvent.setActorUserId(actorId);
    processEvent.setTargetType(targetType);
    processEvent.setTargetId(targetId);
    processEvent.setReason(reason);
    processEvent.setOccurredAt(now);
    return baseMapper.insert(processEvent);
  }

  /** 新增更正事件。 */
  @Override
  public int insertCorrectionEvent(
      String id, String actorId, String versionId, String reason, Instant now) {
    TalentProcessEvent event = new TalentProcessEvent();
    event.setId(id);
    event.setEventType("RESUME_FACTS_CORRECTED");
    event.setActorUserId(actorId);
    event.setTargetType("RESUME_VERSION");
    event.setTargetId(versionId);
    event.setReason(reason);
    event.setOccurredAt(now);
    return baseMapper.insert(event);
  }
}
