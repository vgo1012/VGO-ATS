package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeParseSnapshot;
import com.ezaigc.hr.talent.mapper.ResumeParseSnapshotMapper;
import com.ezaigc.hr.talent.service.ResumeParseSnapshotPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ResumeParseSnapshot 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ResumeParseSnapshotPersistenceServiceImpl
    extends ServiceImpl<ResumeParseSnapshotMapper, ResumeParseSnapshot>
    implements ResumeParseSnapshotPersistenceService {

  /** 查询解析快照操作人缓存。 */
  @Override
  public String findParseSnapshotByCache(String cacheKey) {
    ResumeParseSnapshot snapshot =
        baseMapper.selectOne(
            Wrappers.lambdaQuery(ResumeParseSnapshot.class)
                .select(ResumeParseSnapshot::getId)
                .eq(ResumeParseSnapshot::getCacheKey, cacheKey));
    return snapshot == null ? null : snapshot.getId();
  }

  /** 新增解析快照。 */
  @Override
  public int insertParseSnapshot(
      String id,
      String versionId,
      String extractionId,
      String modelRunId,
      String schemaVersion,
      String promptVersion,
      String cacheKey,
      Instant now) {
    ResumeParseSnapshot snapshot = new ResumeParseSnapshot();
    snapshot.setId(id);
    snapshot.setResumeVersionId(versionId);
    snapshot.setExtractionRunId(extractionId);
    snapshot.setModelRunId(modelRunId);
    snapshot.setSchemaVersion(schemaVersion);
    snapshot.setPromptVersion(promptVersion);
    snapshot.setCacheKey(cacheKey);
    snapshot.setCreateTime(now);
    return baseMapper.insert(snapshot);
  }
}
