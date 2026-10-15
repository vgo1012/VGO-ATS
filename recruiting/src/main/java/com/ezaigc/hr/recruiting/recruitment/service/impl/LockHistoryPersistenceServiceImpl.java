package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockHistory;
import com.ezaigc.hr.recruiting.recruitment.mapper.LockHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** LockHistory 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class LockHistoryPersistenceServiceImpl extends ServiceImpl<LockHistoryMapper, LockHistory>
    implements LockHistoryPersistenceService {

  /** 新增历史。 */
  @Override
  public int insertHistory(
      String id,
      String lockId,
      String candidateId,
      String action,
      String fromOwner,
      String toOwner,
      String progressType,
      String reason,
      String actorId,
      Instant now) {
    LockHistory history = new LockHistory();
    history.setId(id);
    history.setLockId(lockId);
    history.setCandidateId(candidateId);
    history.setActionCode(action);
    history.setFromOwnerUserId(fromOwner);
    history.setToOwnerUserId(toOwner);
    history.setProgressType(progressType);
    history.setReason(reason);
    history.setActorUserId(actorId);
    history.setOccurredAt(now);
    return baseMapper.insert(history);
  }

  @Override
  public List<LockHistory> history(String candidateId, int limit) {
    return baseMapper
        .selectPage(
            new Page<>(1, limit, false),
            Wrappers.lambdaQuery(LockHistory.class)
                .eq(LockHistory::getCandidateId, candidateId)
                .orderByDesc(LockHistory::getOccurredAt, LockHistory::getId))
        .getRecords();
  }
}
