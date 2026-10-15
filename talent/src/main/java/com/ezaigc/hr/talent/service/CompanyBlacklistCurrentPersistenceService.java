package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import java.time.Instant;

/** CompanyBlacklistCurrent 实体的持久化服务。 */
public interface CompanyBlacklistCurrentPersistenceService
    extends IService<CompanyBlacklistCurrent> {

  int isBlacklisted(String candidateId);

  /** 候选人锁内使用当前读，避免事务旧快照遗漏已生效风险。 */
  CompanyBlacklistCurrent lockCurrent(String candidateId);

  /** 首次原子插入；已有投影只允许替换读取到的原事实。 */
  int projectBlacklist(String candidateId, String expectedRecordId, String recordId,
      boolean active, String actorId, Instant now);
}
