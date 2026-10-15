package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockHistory;
import java.time.Instant;
import java.util.List;

/** LockHistory 实体的持久化服务。 */
public interface LockHistoryPersistenceService extends IService<LockHistory> {

  /** 新增历史。 */
  int insertHistory(
      String id,
      String lockId,
      String candidateId,
      String action,
      String fromOwner,
      String toOwner,
      String progressType,
      String reason,
      String actorId,
      Instant now);

  List<LockHistory> history(String candidateId, int limit);
}
