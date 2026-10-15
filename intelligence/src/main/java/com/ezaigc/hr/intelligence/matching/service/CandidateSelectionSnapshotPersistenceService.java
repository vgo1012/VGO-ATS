package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.CandidateSelectionSnapshot;
import java.time.Instant;

/** CandidateSelectionSnapshot 实体的持久化服务。 */
public interface CandidateSelectionSnapshotPersistenceService
    extends IService<CandidateSelectionSnapshot> {

  /** 新增选择。 */
  int insertSelection(
      String id, String jdVersionId, String actorId, int count, String hash, Instant now);
}
