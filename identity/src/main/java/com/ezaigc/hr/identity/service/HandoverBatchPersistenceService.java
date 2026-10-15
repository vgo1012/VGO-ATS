package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import java.time.Instant;

/** HandoverBatchRecord 实体的持久化服务。 */
public interface HandoverBatchPersistenceService extends IService<HandoverBatchRecord> {

  /** 查询交接操作人幂等。 */
  HandoverBatchRecord findHandoverByIdempotency(String actorId, String idempotencyKey);

  HandoverBatchRecord findHandoverById(String id);

  /** 新增交接批次。 */
  int insertHandoverBatch(
      String id,
      String sourceUserId,
      String targetUserId,
      String selectionMode,
      String status,
      int totalCount,
      int unresolvedCount,
      String reason,
      String actorId,
      Instant startedAt,
      Instant completedAt,
      String idempotencyKey);
}
