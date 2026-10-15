package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OwnershipHistory;
import com.ezaigc.hr.recruiting.recruitment.mapper.OwnershipHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentOwnershipHistoryPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** OwnershipHistory 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class RecruitmentOwnershipHistoryPersistenceServiceImpl
    extends ServiceImpl<OwnershipHistoryMapper, OwnershipHistory>
    implements RecruitmentOwnershipHistoryPersistenceService {

  /** 新增归属。 */
  @Override
  public int insertOwnership(
      String id,
      String applicationId,
      String type,
      String fromUserId,
      String toUserId,
      String reason,
      String actorId,
      Instant now) {
    OwnershipHistory history = new OwnershipHistory();
    history.setId(id);
    history.setApplicationId(applicationId);
    history.setActionType(type);
    history.setFromUserId(fromUserId);
    history.setToUserId(toUserId);
    history.setReason(reason);
    history.setOperatedByUserId(actorId);
    history.setCreateTime(now);
    return affected(this.save(history));
  }

  /** 查询归属历史。 */
  @Override
  public List<OwnershipHistory> findOwnershipHistory(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(OwnershipHistory.class)
            .eq(OwnershipHistory::getApplicationId, applicationId)
            .orderByAsc(OwnershipHistory::getCreateTime, OwnershipHistory::getId));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
