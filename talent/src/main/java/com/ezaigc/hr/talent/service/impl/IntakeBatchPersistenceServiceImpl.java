package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.enums.IntakeBatchStatus;
import com.ezaigc.hr.talent.mapper.IntakeBatchMapper;
import com.ezaigc.hr.talent.service.IntakeBatchPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** IntakeBatch 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class IntakeBatchPersistenceServiceImpl extends ServiceImpl<IntakeBatchMapper, IntakeBatch>
    implements IntakeBatchPersistenceService {

  /** 新增批次。 */
  @Override
  public int insertBatch(
      String id,
      String channelId,
      String actorId,
      String ownerId,
      String departmentId,
      String jdVersionId,
      Instant now) {
    IntakeBatch batch = new IntakeBatch();
    batch.setId(id);
    batch.setSourceChannelId(channelId);
    batch.setSourceRecruiterUserId(actorId);
    batch.setDefaultOwnerUserId(ownerId);
    batch.setOwnerDepartmentId(departmentId);
    batch.setJdVersionId(jdVersionId);
    batch.setStatus(IntakeBatchStatus.OPEN.getCode());
    batch.setCreateId(actorId);
    batch.setCreateTime(now);
    batch.setUpdateId(actorId);
    batch.setUpdateTime(now);
    return baseMapper.insert(batch);
  }

  /** 查询批次。 */
  @Override
  public IntakeBatch findBatch(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public IntakeBatch findBatchWithProgress(String id) {
    return baseMapper.findBatchWithProgress(id);
  }

  @Override
  public List<IntakeBatch> findBatches(String actorId, boolean scopeAll, Set<String> managedUserIds,
      Instant cursorTime, String cursorId, int limit) {
    return baseMapper.findBatches(actorId, scopeAll, managedUserIds, cursorTime, cursorId, limit);
  }

  /** 为尚未关联岗位的历史批次原子补充岗位版本。 */
  @Override
  public int linkJdVersion(String id, String jdVersionId, String actorId, Instant now) {
    return baseMapper.linkJdVersion(id, jdVersionId, actorId, now);
  }
}
