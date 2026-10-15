package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.dto.ResumeProjectionCursorDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeSearchStateRow;
import com.ezaigc.hr.talent.domain.dto.SearchFactContactDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.mapper.ResumeParsingMapper;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeParsingPersistenceServiceImpl
    extends ServiceImpl<ResumeParsingMapper, FactSnapshot>
    implements ResumeParsingPersistenceService {
  @Override
  public ResumeSearchStateRow findCurrentSearchState(String candidateId) {
    return baseMapper.findCurrentSearchState(candidateId);
  }

  @Override
  public List<ResumeSearchStateRow> findCurrentSearchStatesAfter(String afterCandidateId, int limit) {
    return baseMapper.findCurrentSearchStatesAfter(afterCandidateId, limit);
  }

  /** 查询当前事实快照。 */
  @Override
  public FactSnapshot findCurrentFactSnapshot(String versionId) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(FactSnapshot.class)
            .eq(FactSnapshot::getResumeVersionId, versionId)
            .eq(FactSnapshot::getCurrentMarker, "Y"));
  }

  @Override
  public List<SearchFactContactDTO> findLatestAvailableFactContacts(Collection<String> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return baseMapper.findLatestAvailableFactContacts(versionIds);
  }

  @Override
  public List<FactSnapshot> findCurrentFactSnapshotDetails(Collection<String> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return baseMapper.selectList(
        Wrappers.lambdaQuery(FactSnapshot.class)
            .in(FactSnapshot::getResumeVersionId, versionIds)
            .eq(FactSnapshot::getCurrentMarker, "Y"));
  }

  /** 查询事实快照操作人解析。 */
  @Override
  public FactSnapshot findFactSnapshotByParse(String parseId) {
    var snapshots = baseMapper.selectPage(new Page<FactSnapshot>(1, 1, false),
        Wrappers.lambdaQuery(FactSnapshot.class)
            .eq(FactSnapshot::getParseSnapshotId, parseId)
            .orderByAsc(FactSnapshot::getSnapshotVersion)).getRecords();
    return snapshots.isEmpty() ? null : snapshots.getFirst();
  }

  /** 查询事实快照。 */
  @Override
  public FactSnapshot findFactSnapshot(String id) {
    return baseMapper.selectById(id);
  }

  /** 清除当前事实。 */
  @Override
  public int clearCurrentFact(String versionId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(FactSnapshot.class)
            .eq(FactSnapshot::getResumeVersionId, versionId)
            .eq(FactSnapshot::getCurrentMarker, "Y")
            .set(FactSnapshot::getCurrentMarker, null)
            .set(FactSnapshot::getUpdateId, actorId)
            .set(FactSnapshot::getUpdateTime, now));
  }

  /** 新增事实快照。 */
  @Override
  public int insertFactSnapshot(
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
      Instant now) {
    FactSnapshot snapshot = new FactSnapshot();
    snapshot.setId(id);
    snapshot.setResumeVersionId(versionId);
    snapshot.setParseSnapshotId(parseId);
    snapshot.setPreviousSnapshotId(previousId);
    snapshot.setSnapshotVersion(snapshotVersion);
    snapshot.setSourceType(sourceType);
    snapshot.setCurrentMarker("Y");
    snapshot.setFullName(fullName);
    snapshot.setEmail(email);
    snapshot.setPhone(phone);
    snapshot.setCurrentRole(currentRole);
    snapshot.setCity(city);
    snapshot.setAge(age);
    snapshot.setGender(gender);
    snapshot.setJobIntention(jobIntention);
    snapshot.setExt(ext);
    snapshot.setCorrectedByUserId(actorId);
    snapshot.setCorrectionReason(reason);
    snapshot.setCreateTime(now);
    return baseMapper.insert(snapshot);
  }

  @Override
  public ResumeResourceEntity findResumeResource(String versionId) {
    return baseMapper.findResumeResource(versionId);
  }

  @Override
  public ResumeResourceEntity findResumeResourceForUpdate(String versionId, String candidateId) {
    return baseMapper.findResumeResourceForUpdate(versionId, candidateId);
  }

  @Override
  public List<ResumeResourceEntity> findResumeResources(Collection<String> versionIds) {
    if (versionIds == null || versionIds.isEmpty()) {
      return List.of();
    }
    return baseMapper.findResumeResources(versionIds);
  }

  @Override
  public FactSnapshot findCurrentFactSnapshotForUpdate(String versionId) {
    return baseMapper.findCurrentFactSnapshotForUpdate(versionId);
  }

  @Override
  public List<ResumeProjectionCursorDTO> findCurrentFactSnapshotsAfter(
      Instant afterCreatedAt, String afterSnapshotId, int limit) {
    return baseMapper.findCurrentFactSnapshotsAfter(afterCreatedAt, afterSnapshotId, limit);
  }

  @Override
  public int insertCorrection(
      String id,
      String versionId,
      String previousId,
      String newId,
      String actorId,
      String reason,
      String capabilities,
      Instant now) {
    return baseMapper.insertCorrection(
        id, versionId, previousId, newId, actorId, reason, capabilities, now);
  }

}
