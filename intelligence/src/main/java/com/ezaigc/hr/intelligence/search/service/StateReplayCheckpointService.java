package com.ezaigc.hr.intelligence.search.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import java.time.Instant;

public interface StateReplayCheckpointService extends IService<StateReplayCheckpoint> {
  void initialize(StateReplayCheckpoint checkpoint);
  StateReplayCheckpoint checkpoint();
  boolean bindIndex(String indexName, long version, String actorId, Instant now);
  boolean advance(String candidateId, boolean completed, int count, long version, String actorId, Instant now);

  boolean resetForApprovedIndex(String indexName, String actorId, Instant now);
}
