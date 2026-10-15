package com.ezaigc.hr.intelligence.search.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import java.time.Instant;

public interface ProjectionCheckpointService extends IService<ProjectionCheckpoint> {
  void initialize(ProjectionCheckpoint checkpoint);

  ProjectionCheckpoint checkpoint();

  /** 用版本 CAS 为新索引开启独立投影游标，旧在途页面不得推进该游标。 */
  boolean bindIndex(String indexName, long version, Instant epoch, String updateId, Instant updateTime);

  boolean advance(
      Instant createTime,
      String snapshotId,
      int count,
      long version,
      String updateId,
      Instant updateTime);

  void reset(Instant epoch, String updateId, Instant updateTime);

  SearchIndexTarget approvedIndex();

  SearchIndexTarget lockApprovedIndex();

  boolean approveIndex(String indexName, long generation, String actorId, Instant now);

  /** 在批准短事务中重绑 observed 游标并使在途旧页失去推进权限。 */
  boolean resetForApprovedIndex(String indexName, Instant epoch, String actorId, Instant now);
}
