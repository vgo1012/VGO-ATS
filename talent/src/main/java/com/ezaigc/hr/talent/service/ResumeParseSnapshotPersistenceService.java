package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ResumeParseSnapshot;
import java.time.Instant;

/** ResumeParseSnapshot 实体的持久化服务。 */
public interface ResumeParseSnapshotPersistenceService extends IService<ResumeParseSnapshot> {

  /** 查询解析快照操作人缓存。 */
  String findParseSnapshotByCache(String cacheKey);

  /** 新增解析快照。 */
  int insertParseSnapshot(
      String id,
      String versionId,
      String extractionId,
      String modelRunId,
      String schemaVersion,
      String promptVersion,
      String cacheKey,
      Instant now);
}
