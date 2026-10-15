package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** IntakeBatch 实体的持久化服务。 */
public interface IntakeBatchPersistenceService extends IService<IntakeBatch> {

  /** 新增批次。 */
  int insertBatch(
      String id,
      String channelId,
      String actorId,
      String ownerId,
      String departmentId,
      String jdVersionId,
      Instant now);

  /** 未关联岗位的旧接收入口保留兼容。 */
  default int insertBatch(String id, String channelId, String actorId, String ownerId, String departmentId, Instant now) {
    return insertBatch(id, channelId, actorId, ownerId, departmentId, null, now);
  }

  /** 查询批次。 */
  IntakeBatch findBatch(String id);

  /** 查询批次及实时解析进度。 */
  IntakeBatch findBatchWithProgress(String id);

  /** 在默认归属范围内按创建时间分页查询批次及解析进度。 */
  List<IntakeBatch> findBatches(String actorId, boolean scopeAll, Set<String> managedUserIds,
      Instant cursorTime, String cursorId, int limit);

  /** 为尚未关联岗位的历史批次原子补充岗位版本。 */
  int linkJdVersion(String id, String jdVersionId, String actorId, Instant now);
}
