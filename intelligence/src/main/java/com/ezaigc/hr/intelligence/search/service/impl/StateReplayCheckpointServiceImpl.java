package com.ezaigc.hr.intelligence.search.service.impl;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.INDEX_NAME_PATTERN;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_PROJECTION;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import com.ezaigc.hr.intelligence.search.mapper.StateReplayCheckpointMapper;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 每次页面写入完成后通过版本 CAS 推进；迟到页面不能更新新的索引进度。 */
@Service
public class StateReplayCheckpointServiceImpl
    extends ServiceImpl<StateReplayCheckpointMapper, StateReplayCheckpoint>
    implements StateReplayCheckpointService {
  @Override
  public boolean resetForApprovedIndex(String indexName, String actorId, Instant now) {
    return update(Wrappers.<StateReplayCheckpoint>lambdaUpdate()
        .set(StateReplayCheckpoint::getIndexName, indexName)
        .set(StateReplayCheckpoint::getLastCandidateId, MIN_ID)
        .set(StateReplayCheckpoint::isCompleted, false)
        .set(StateReplayCheckpoint::getProjectedCount, 0)
        .setIncrBy(StateReplayCheckpoint::getVersion, 1)
        .set(StateReplayCheckpoint::getUpdateId, actorId)
        .set(StateReplayCheckpoint::getUpdateTime, now)
        .eq(StateReplayCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION));
  }
  @Override
  public void initialize(StateReplayCheckpoint checkpoint) { baseMapper.insertIgnore(checkpoint); }

  @Override
  public StateReplayCheckpoint checkpoint() { return getById(RESUME_FACTS_PROJECTION); }

  @Override
  public boolean bindIndex(String indexName, long version, String actorId, Instant now) {
    if (indexName == null || !indexName.matches(INDEX_NAME_PATTERN)) {
      throw new IllegalArgumentException("invalid search replay index");
    }
    return update(Wrappers.<StateReplayCheckpoint>lambdaUpdate()
        .set(StateReplayCheckpoint::getIndexName, indexName)
        .set(StateReplayCheckpoint::getLastCandidateId, MIN_ID)
        .set(StateReplayCheckpoint::isCompleted, false)
        .set(StateReplayCheckpoint::getProjectedCount, 0)
        .setIncrBy(StateReplayCheckpoint::getVersion, 1)
        .set(StateReplayCheckpoint::getUpdateId, actorId)
        .set(StateReplayCheckpoint::getUpdateTime, now)
        .eq(StateReplayCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION)
        .eq(StateReplayCheckpoint::getVersion, version));
  }

  @Override
  public boolean advance(String candidateId, boolean completed, int count, long version, String actorId, Instant now) {
    return update(Wrappers.<StateReplayCheckpoint>lambdaUpdate()
        .set(StateReplayCheckpoint::getLastCandidateId, candidateId)
        .set(StateReplayCheckpoint::isCompleted, completed)
        .setIncrBy(StateReplayCheckpoint::getProjectedCount, count)
        .setIncrBy(StateReplayCheckpoint::getVersion, 1)
        .set(StateReplayCheckpoint::getUpdateId, actorId)
        .set(StateReplayCheckpoint::getUpdateTime, now)
        .eq(StateReplayCheckpoint::getProjectionName, RESUME_FACTS_PROJECTION)
        .eq(StateReplayCheckpoint::getVersion, version));
  }
}
