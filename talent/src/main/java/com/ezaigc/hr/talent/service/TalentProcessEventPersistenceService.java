package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import java.time.Instant;

/** TalentProcessEvent 实体的持久化服务。 */
public interface TalentProcessEventPersistenceService extends IService<TalentProcessEvent> {

  int insertEvent(
      String id,
      String event,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      Instant now);

  /** 新增更正事件。 */
  int insertCorrectionEvent(
      String id, String actorId, String versionId, String reason, Instant now);
}
