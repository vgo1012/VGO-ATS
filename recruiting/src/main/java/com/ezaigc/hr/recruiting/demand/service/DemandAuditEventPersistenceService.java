package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandAuditEventEntity;
import java.time.Instant;

/** DemandAuditEventEntity 实体的持久化服务。 */
public interface DemandAuditEventPersistenceService extends IService<DemandAuditEventEntity> {

  /** 新增审计。 */
  int insertAudit(
      String id,
      String eventType,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      Instant now);
}
