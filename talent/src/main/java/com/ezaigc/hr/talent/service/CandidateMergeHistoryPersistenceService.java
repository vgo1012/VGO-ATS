package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.CandidateMergeHistory;
import java.time.Instant;

/** CandidateMergeHistory 实体的持久化服务。 */
public interface CandidateMergeHistoryPersistenceService extends IService<CandidateMergeHistory> {

  /** 新增合并历史。 */
  int insertMergeHistory(
      String id,
      String survivorId,
      String mergedId,
      String conflictId,
      long survivorVersion,
      long mergedVersion,
      String actorId,
      String reason,
      Instant now);

  /** 查询合并保留。 */
  String findMergeSurvivor(String mergedId);
}
