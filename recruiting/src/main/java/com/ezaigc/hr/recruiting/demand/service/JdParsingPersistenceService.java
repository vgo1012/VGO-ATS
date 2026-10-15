package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import java.time.Instant;

public interface JdParsingPersistenceService
    extends IService<JdFactSnapshotEntity>, JdParsingSqlOperations {
  /** 新增快照。 */
  int insertSnapshot(
      String id,
      String versionId,
      String modelRunId,
      int snapshotVersion,
      String schemaVersion,
      String factsJson,
      String sourceSnapshotId,
      Instant now);

  /** 查询快照。 */
  JdFactSnapshotEntity findSnapshot(String id);

  /** 查询当前。 */
  JdFactSnapshotEntity findCurrent(String versionId);

  /** 清除当前。 */
  int clearCurrent(String versionId, Instant now);
}
