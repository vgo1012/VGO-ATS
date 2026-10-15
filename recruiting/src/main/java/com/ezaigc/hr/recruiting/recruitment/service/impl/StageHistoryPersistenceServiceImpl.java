package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.StageHistory;
import com.ezaigc.hr.recruiting.recruitment.mapper.StageHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** StageHistory 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class StageHistoryPersistenceServiceImpl
    extends ServiceImpl<StageHistoryMapper, StageHistory>
    implements StageHistoryPersistenceService {

  /** 新增阶段历史。 */
  @Override
  public int insertStageHistory(
      String id,
      String applicationId,
      String fromStage,
      String toStage,
      String reason,
      String actorId,
      Instant now) {
    StageHistory history = new StageHistory();
    history.setId(id);
    history.setApplicationId(applicationId);
    history.setFromStage(fromStage);
    history.setToStage(toStage);
    history.setReason(reason);
    history.setOperatedByUserId(actorId);
    history.setOccurredAt(now);
    return affected(this.save(history));
  }

  /** 查询阶段历史。 */
  @Override
  public List<StageHistory> findStageHistory(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(StageHistory.class)
            .eq(StageHistory::getApplicationId, applicationId)
            .orderByAsc(StageHistory::getOccurredAt, StageHistory::getId));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
