package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestrictionCurrent;
import java.time.Instant;

/** ClientRestrictionCurrent 实体的持久化服务。 */
public interface ClientRestrictionCurrentPersistenceService
    extends IService<ClientRestrictionCurrent> {

  int hasRestriction(String clientId, String candidateId, Instant now);
}
