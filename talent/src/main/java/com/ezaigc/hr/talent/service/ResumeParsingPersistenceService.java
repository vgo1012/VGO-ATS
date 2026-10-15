package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.dto.ResumeProjectionCursorDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeSearchStateRow;
import com.ezaigc.hr.talent.domain.dto.SearchFactContactDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ResumeParsingPersistenceService extends IService<FactSnapshot> {
  ResumeSearchStateRow findCurrentSearchState(String candidateId);

  List<ResumeSearchStateRow> findCurrentSearchStatesAfter(String afterCandidateId, int limit);
  /** 查询简历资源。 */
  ResumeResourceEntity findResumeResource(String versionId);

  /** 在调用方先锁候选人与版本后，当前读校验资源仍属于预期候选人。 */
  ResumeResourceEntity findResumeResourceForUpdate(String versionId, String candidateId);

  /** 批量查询可用简历资源。 */
  List<ResumeResourceEntity> findResumeResources(Collection<String> versionIds);

  /** 查询当前事实快照。 */
  FactSnapshot findCurrentFactSnapshot(String versionId);

  List<SearchFactContactDTO> findLatestAvailableFactContacts(Collection<String> versionIds);

  /** 批量查询完整的当前事实快照。 */
  List<FactSnapshot> findCurrentFactSnapshotDetails(Collection<String> versionIds);

  /** 查询当前事实快照用于更新。 */
  FactSnapshot findCurrentFactSnapshotForUpdate(String versionId);

  /** 查询事实快照操作人解析。 */
  FactSnapshot findFactSnapshotByParse(String parseId);

  /** 查询事实快照。 */
  FactSnapshot findFactSnapshot(String id);

  /** 查询当前事实快照列表变更后。 */
  List<ResumeProjectionCursorDTO> findCurrentFactSnapshotsAfter(
      Instant afterCreatedAt, String afterSnapshotId, int limit);

  /** 清除当前事实。 */
  int clearCurrentFact(String versionId, String actorId, Instant now);

  /** 新增事实快照。 */
  int insertFactSnapshot(
      String id,
      String versionId,
      String parseId,
      String previousId,
      int snapshotVersion,
      String sourceType,
      String fullName,
      String email,
      String phone,
      String currentRole,
      String city,
      Integer age,
      String gender,
      String jobIntention,
      String ext,
      String actorId,
      String reason,
      Instant now);

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
