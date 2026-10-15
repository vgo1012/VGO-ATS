package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.AccountHistory;
import java.time.Instant;

/** AccountHistory 实体的持久化服务。 */
public interface AccountHistoryPersistenceService extends IService<AccountHistory> {

  /** 新增账户历史。 */
  int insertAccountHistory(
      String id,
      String userId,
      String changeType,
      String beforeSnapshot,
      String afterSnapshot,
      String reason,
      String actorId,
      Instant now,
      long accountVersion);
}
