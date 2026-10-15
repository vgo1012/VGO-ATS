package com.ezaigc.hr.intelligence.search.service.impl;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.INDEX_NAME_PATTERN;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_PROJECTION;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_APPROVED_INDEX;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.mapper.ProjectionCheckpointMapper;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 投影检查点业务服务实现。 */
@Service
public class ProjectionCheckpointServiceImpl
    extends ServiceImpl<ProjectionCheckpointMapper, ProjectionCheckpoint>
    implements ProjectionCheckpointService {

  @Override
  public SearchIndexTarget approvedIndex() {
    return baseMapper.approvedCurrent(RESUME_FACTS_APPROVED_INDEX);
  }

  @Override
  public SearchIndexTarget lockApprovedIndex() {
    return baseMapper.approvedForUpdate(RESUME_FACTS_APPROVED_INDEX);
  }

  @Override
  public boolean approveIndex(String indexName, long generation, String actorId, Instant now) {
    if (indexName == null || !indexName.matches(INDEX_NAME_PATTERN)
        || generation < 0 || generation >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("invalid search approval target or generation");
    }
    return update(Wrappers.<ProjectionCheckpoint>lambdaUpdate()
        .set(ProjectionCheckpoint::getIndexName, indexName)
        .setIncrBy(ProjectionCheckpoint::getVersion, 1)
        .set(ProjectionCheckpoint::getUpdateId, actorId)
        .set(ProjectionCheckpoint::getUpdateTime, now)
        .eq(ProjectionCheckpoint::getProjectionName, RESUME_FACTS_APPROVED_INDEX)
        .eq(ProjectionCheckpoint::getVersion, generation));
  }

  @Override
  public boolean resetForApprovedIndex(String indexName, Instant epoch, String actorId, Instant now) {
    return update(Wrappers.<ProjectionCheckpoint>lambdaUpdate()
        .set(ProjectionCheckpoint::getIndexName, indexName)
        .set(ProjectionCheckpoint::getLastCreatedAt, epoch)
        .set(ProjectionCheckpoint::getLastSnapshotId, MIN_ID)
        .set(ProjectionCheckpoint::getProjectedCount, 0)
        .setIncrBy(ProjectionCheckpoint::getVersion, 1)
        .set(ProjectionCheckpoint::getUpdateId, actorId)
        .set(ProjectionCheckpoint::getUpdateTime, now)
        .eq(ProjectionCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION));
  }

  @Override
  public void initialize(ProjectionCheckpoint checkpoint) {
    baseMapper.insertIgnore(checkpoint);
  }

  @Override
  public ProjectionCheckpoint checkpoint() {
    return getById(RESUME_FACTS_PROJECTION);
  }

  @Override
  public boolean bindIndex(String indexName, long version, Instant epoch, String updateId, Instant updateTime) {
    if (indexName == null || !indexName.matches(INDEX_NAME_PATTERN)) {
      throw new IllegalArgumentException("invalid search projection index");
    }
    return update(Wrappers.<ProjectionCheckpoint>lambdaUpdate()
        .set(ProjectionCheckpoint::getIndexName, indexName)
        .set(ProjectionCheckpoint::getLastCreatedAt, epoch)
        .set(ProjectionCheckpoint::getLastSnapshotId, MIN_ID)
        .set(ProjectionCheckpoint::getProjectedCount, 0)
        .setIncrBy(ProjectionCheckpoint::getVersion, 1)
        .set(ProjectionCheckpoint::getUpdateId, updateId)
        .set(ProjectionCheckpoint::getUpdateTime, updateTime)
        .eq(ProjectionCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION)
        .eq(ProjectionCheckpoint::getVersion, version));
  }

  @Override
  public boolean advance(
      Instant createTime,
      String snapshotId,
      int count,
      long version,
      String updateId,
      Instant updateTime) {
    return update(
        Wrappers.<ProjectionCheckpoint>lambdaUpdate()
            .set(ProjectionCheckpoint::getLastCreatedAt, createTime)
            .set(ProjectionCheckpoint::getLastSnapshotId, snapshotId)
            .setIncrBy(ProjectionCheckpoint::getProjectedCount, count)
            .setIncrBy(ProjectionCheckpoint::getVersion, 1)
            .set(ProjectionCheckpoint::getUpdateId, updateId)
            .set(ProjectionCheckpoint::getUpdateTime, updateTime)
            .eq(ProjectionCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION)
            .eq(ProjectionCheckpoint::getVersion, version));
  }

  @Override
  public void reset(Instant epoch, String updateId, Instant updateTime) {
    update(
        Wrappers.<ProjectionCheckpoint>lambdaUpdate()
            .set(ProjectionCheckpoint::getIndexName, null)
            .set(ProjectionCheckpoint::getLastCreatedAt, epoch)
            .set(ProjectionCheckpoint::getLastSnapshotId, MIN_ID)
            .set(ProjectionCheckpoint::getProjectedCount, 0)
            .setIncrBy(ProjectionCheckpoint::getVersion, 1)
            .set(ProjectionCheckpoint::getUpdateId, updateId)
            .set(ProjectionCheckpoint::getUpdateTime, updateTime)
            .eq(ProjectionCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION));
  }
}
