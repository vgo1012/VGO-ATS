package com.ezaigc.hr.talent.mapper;

import com.ezaigc.hr.talent.domain.dto.ResumeProjectionCursorDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import java.time.Instant;
import java.util.List;

public interface ResumeParsingSqlOperations {
  /** 查询简历资源。 */
  ResumeResourceEntity findResumeResource(String versionId);

  ResumeResourceEntity findResumeResourceForUpdate(String versionId, String candidateId);

  /** 查询当前事实快照用于更新。 */
  FactSnapshot findCurrentFactSnapshotForUpdate(String versionId);

  /** 查询当前事实快照列表变更后。 */
  List<ResumeProjectionCursorDTO> findCurrentFactSnapshotsAfter(
      Instant afterCreatedAt, String afterSnapshotId, int limit);

  /** 新增更正。 */
  int insertCorrection(
      String id,
      String versionId,
      String previousId,
      String newId,
      String actorId,
      String reason,
      String capabilities,
      Instant now);
}
