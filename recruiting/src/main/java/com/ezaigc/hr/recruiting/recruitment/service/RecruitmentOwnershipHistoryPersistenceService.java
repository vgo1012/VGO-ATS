package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OwnershipHistory;
import java.time.Instant;
import java.util.List;

/** OwnershipHistory 实体的持久化服务。 */
public interface RecruitmentOwnershipHistoryPersistenceService extends IService<OwnershipHistory> {

  /** 新增归属。 */
  int insertOwnership(
      String id,
      String applicationId,
      String type,
      String fromUserId,
      String toUserId,
      String reason,
      String actorId,
      Instant now);

  /** 查询归属历史。 */
  List<OwnershipHistory> findOwnershipHistory(String applicationId);
}
