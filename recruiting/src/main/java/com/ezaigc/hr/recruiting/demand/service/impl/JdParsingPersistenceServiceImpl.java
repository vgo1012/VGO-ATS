package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.enums.JdFactSourceType;
import com.ezaigc.hr.recruiting.demand.mapper.JdParsingMapper;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class JdParsingPersistenceServiceImpl
    extends ServiceImpl<JdParsingMapper, JdFactSnapshotEntity>
    implements JdParsingPersistenceService {
  /** 新增快照。 */
  @Override
  public int insertSnapshot(
      String id,
      String versionId,
      String modelRunId,
      int snapshotVersion,
      String schemaVersion,
      String factsJson,
      String sourceSnapshotId,
      Instant now) {
    JdFactSnapshotEntity snapshot = new JdFactSnapshotEntity();
    snapshot.setId(id);
    snapshot.setJdVersionId(versionId);
    snapshot.setModelRunId(modelRunId);
    snapshot.setSnapshotVersion(snapshotVersion);
    snapshot.setSourceType(JdFactSourceType.AI_PARSE.getCode());
    snapshot.setCurrentMarker("Y");
    snapshot.setSchemaVersion(schemaVersion);
    snapshot.setFactsJson(factsJson);
    if (sourceSnapshotId != null) snapshot.setRemark("复用来源 JD 事实快照 " + sourceSnapshotId);
    snapshot.setCreateId(PlatformActorConstants.SYSTEM_ACTOR_ID);
    snapshot.setCreateTime(now);
    snapshot.setUpdateId(PlatformActorConstants.SYSTEM_ACTOR_ID);
    snapshot.setUpdateTime(now);
    return save(snapshot) ? 1 : 0;
  }

  /** 查询快照。 */
  @Override
  public JdFactSnapshotEntity findSnapshot(String id) {
    return getById(id);
  }

  /** 查询当前。 */
  @Override
  public JdFactSnapshotEntity findCurrent(String versionId) {
    return getOne(
        Wrappers.lambdaQuery(JdFactSnapshotEntity.class)
            .eq(JdFactSnapshotEntity::getJdVersionId, versionId)
            .eq(JdFactSnapshotEntity::getCurrentMarker, "Y"));
  }

  /** 清除当前。 */
  @Override
  public int clearCurrent(String versionId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(JdFactSnapshotEntity.class)
            .eq(JdFactSnapshotEntity::getJdVersionId, versionId)
            .eq(JdFactSnapshotEntity::getCurrentMarker, "Y")
            .set(JdFactSnapshotEntity::getCurrentMarker, null)
            .set(JdFactSnapshotEntity::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(JdFactSnapshotEntity::getUpdateTime, now));
  }

  @Override
  public int completeModelRun(
      String id,
      String provider,
      String modelVersion,
      String objectKey,
      String hash,
      long size,
      String encryptionVersion,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now) {
    return baseMapper.completeModelRun(
        id,
        provider,
        modelVersion,
        objectKey,
        hash,
        size,
        encryptionVersion,
        inputTokens,
        outputTokens,
        durationMillis,
        now);
  }

  @Override
  public int failModelRun(
      String id,
      String validationStatus,
      String errorCode,
      String provider,
      String modelVersion,
      String objectKey,
      String hash,
      Long size,
      String encryptionVersion,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now) {
    return baseMapper.failModelRun(
        id,
        validationStatus,
        errorCode,
        provider,
        modelVersion,
        objectKey,
        hash,
        size,
        encryptionVersion,
        inputTokens,
        outputTokens,
        durationMillis,
        now);
  }

  @Override
  public JdFactSnapshotEntity findCurrentForUpdate(String versionId) {
    return baseMapper.findCurrentForUpdate(versionId);
  }
}
