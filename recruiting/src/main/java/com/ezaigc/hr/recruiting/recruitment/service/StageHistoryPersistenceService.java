package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.StageHistory;
import java.time.Instant;
import java.util.List;

/** StageHistory 实体的持久化服务。 */
public interface StageHistoryPersistenceService extends IService<StageHistory> {

  /** 新增阶段历史。 */
  int insertStageHistory(
      String id,
      String applicationId,
      String fromStage,
      String toStage,
      String reason,
      String actorId,
      Instant now);

  /** 查询阶段历史。 */
  List<StageHistory> findStageHistory(String applicationId);
}
