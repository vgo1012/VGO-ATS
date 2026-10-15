package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.dto.IntakeScoringPreparationDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.mapper.TalentIntakeMapper;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 人才接收持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation; permissions and transactions stay in application managers.
 */
@Service
public class TalentIntakePersistenceServiceImpl
    extends ServiceImpl<TalentIntakeMapper, ResumeIntake>
    implements TalentIntakePersistenceService {
  @Override
  public List<String> findCandidateIdsForJdVersion(String jdVersionId, String afterId, int limit) {
    return baseMapper.findCandidateIdsForJdVersion(jdVersionId, afterId, limit);
  }
  @Override
  public List<CandidateResumeRow> findCurrentCandidateResumes(List<String> candidateIds) {
    return candidateIds.isEmpty() ? List.of() : baseMapper.findCurrentCandidateResumes(candidateIds);
  }

  @Override
  public List<CandidateResumeRow> findCandidateResumeHistory(List<String> candidateIds) {
    return candidateIds.isEmpty() ? List.of() : baseMapper.findCandidateResumeHistory(candidateIds);
  }

  @Override
  public List<CandidateResumeRow> findCurrentCandidateResumeVersions(List<String> candidateIds) {
    return candidateIds.isEmpty() ? List.of() : baseMapper.findCurrentCandidateResumeVersions(candidateIds);
  }
  /** 新增接收。 */
  @Override
  public int insertIntake(
      String id,
      String batchId,
      String channelId,
      String actorId,
      String ownerId,
      String departmentId,
      String fileName,
      String mediaType,
      long size,
      String sha256,
      String objectKey,
      String scanStatus,
      String status,
      String failureCode,
      Instant now) {
    ResumeIntake intake = new ResumeIntake();
    intake.setId(id);
    intake.setBatchId(batchId);
    intake.setSourceChannelId(channelId);
    intake.setSourceRecruiterUserId(actorId);
    intake.setOwnerUserId(ownerId);
    intake.setOwnerDepartmentId(departmentId);
    intake.setOriginalFileName(fileName);
    intake.setMediaType(mediaType);
    intake.setSizeBytes(size);
    intake.setContentSha256(sha256);
    intake.setObjectKey(objectKey);
    intake.setScanStatus(scanStatus);
    intake.setStatus(status);
    intake.setFailureCode(failureCode);
    intake.setCreateId(actorId);
    intake.setCreateTime(now);
    intake.setUpdateId(actorId);
    intake.setUpdateTime(now);
    return baseMapper.insert(intake);
  }

  /** 分配接收。 */
  @Override
  public int assignIntake(
      String id, String ownerId, String departmentId, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeIntake.class)
            .eq(ResumeIntake::getId, id)
            .eq(ResumeIntake::getVersion, version)
            .setIncrBy(ResumeIntake::getVersion, 1)
            .notIn(
                ResumeIntake::getStatus,
                ResumeIntakeStatus.IGNORED.getCode(),
                ResumeIntakeStatus.REJECTED.getCode(),
                ResumeIntakeStatus.QUARANTINED.getCode())
            .set(ResumeIntake::getOwnerUserId, ownerId)
            .set(ResumeIntake::getOwnerDepartmentId, departmentId)
            .set(ResumeIntake::getHandledByUserId, actorId)
            .set(ResumeIntake::getUpdateId, actorId)
            .set(ResumeIntake::getUpdateTime, now));
  }

  /** 更新接收状态。 */
  @Override
  public int updateIntakeStatus(
      String id, String status, String failureCode, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeIntake.class)
            .eq(ResumeIntake::getId, id)
            .eq(ResumeIntake::getVersion, version)
            .setIncrBy(ResumeIntake::getVersion, 1)
            .set(ResumeIntake::getStatus, status)
            .set(ResumeIntake::getFailureCode, failureCode)
            .set(ResumeIntake::getHandledByUserId, actorId)
            .set(ResumeIntake::getUpdateId, actorId)
            .set(ResumeIntake::getUpdateTime, now));
  }

  /** 更新接收对象键。 */
  @Override
  public int updateIntakeObjectKey(String id, String objectKey, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeIntake.class)
            .eq(ResumeIntake::getId, id)
            .set(ResumeIntake::getObjectKey, objectKey)
            .set(ResumeIntake::getUpdateId, actorId)
            .set(ResumeIntake::getUpdateTime, now));
  }

  @Override
  public int attachProcessing(String id, String processingId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeIntake.class)
            .eq(ResumeIntake::getId, id)
            .isNull(ResumeIntake::getProcessingId)
            .set(ResumeIntake::getProcessingId, processingId)
            .set(ResumeIntake::getStatus, ResumeIntakeStatus.PARSING.getCode())
            .set(ResumeIntake::getFailureCode, null)
            .set(ResumeIntake::getUpdateId, actorId)
            .set(ResumeIntake::getUpdateTime, now));
  }

  @Override
  public int reparseIntake(String id, long version, String oldProcessingId, String newProcessingId,
      String actorId, Instant now) {
    return baseMapper.reparseIntake(id, version, oldProcessingId, newProcessingId, actorId, now,
        MalwareScanVerdict.CLEAN.getCode());
  }

  @Override
  public List<ResumeIntake> findPendingIntakes(String processingStatus, int limit) {
    return baseMapper.findPendingIntakes(processingStatus, limit);
  }

  @Override
  public IntakeScoringPreparationDTO findScoringPreparationIntake(String processingId) {
    return baseMapper.findScoringPreparationIntake(processingId);
  }

  @Override
  public List<ResumeIntake> findScorableIntakes(int limit) {
    return baseMapper.findScorableIntakes(limit);
  }

  @Override
  public List<ResumeIntake> findAdoptingIntakes(int limit) {
    return baseMapper.findAdoptingIntakes(limit);
  }

  @Override
  public int advanceIntake(String id, String expectedStatus, String nextStatus,
      String failureCode, String actorId, Instant now) {
    return baseMapper.advanceIntake(id, expectedStatus, nextStatus, failureCode, actorId, now);
  }

  @Override
  public int updateScore(String id, long expectedVersion, String runId, String scoreStatus, String failureCode,
      String actorId, Instant now) {
    return baseMapper.updateScore(id, expectedVersion, runId, scoreStatus, failureCode, actorId, now);
  }

  /** 确认接收。 */
  @Override
  public int confirmIntake(
      String id,
      String candidateId,
      String resumeVersionId,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeIntake.class)
            .eq(ResumeIntake::getId, id)
            .eq(ResumeIntake::getVersion, version)
            .setIncrBy(ResumeIntake::getVersion, 1)
            .in(ResumeIntake::getStatus,
                ResumeIntakeStatus.READY_FOR_CONFIRMATION.getCode(),
                ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.getCode())
            .set(ResumeIntake::getStatus, ResumeIntakeStatus.CONFIRMED.getCode())
            .set(ResumeIntake::getCandidateId, candidateId)
            .set(ResumeIntake::getResumeVersionId, resumeVersionId)
            .set(ResumeIntake::getHandledByUserId, actorId)
            .set(ResumeIntake::getUpdateId, actorId)
            .set(ResumeIntake::getUpdateTime, now));
  }

  @Override
  public int redirectCandidateIntakes(
      String survivorId, String mergedId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeIntake.class)
            .eq(ResumeIntake::getCandidateId, mergedId)
            .set(ResumeIntake::getCandidateId, survivorId)
            .set(ResumeIntake::getUpdateId, actorId)
            .set(ResumeIntake::getUpdateTime, now));
  }

  @Override
  public int incrementBatch(String id, int succeeded, int failed, String actorId, Instant now) {
    return baseMapper.incrementBatch(id, succeeded, failed, actorId, now);
  }

  @Override
  public int completeBatch(String id, String actorId, Instant now) {
    return baseMapper.completeBatch(id, actorId, now);
  }

  @Override
  public ResumeIntake findIntake(String id) {
    return baseMapper.findIntake(id);
  }

  @Override
  public long countIntakes(
      String batchId, String status, String importStatus, Boolean duplicateContent, String query, String name,
      Instant from, Instant to,
      String actorId, boolean scopeAll, Set<String> managedUserIds, Set<String> visibleDepartmentIds) {
    return baseMapper.countIntakes(batchId, status, importStatus, duplicateContent, query, name, from, to,
        actorId, scopeAll, managedUserIds, visibleDepartmentIds);
  }

  @Override
  public List<ResumeIntake> findIntakes(
      String batchId,
      String status,
      String importStatus,
      Boolean duplicateContent,
      String query,
      String name,
      Instant from,
      Instant to,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findIntakes(
        batchId,
        status,
        importStatus,
        duplicateContent,
        query,
        name,
        from,
        to,
        actorId,
        scopeAll,
        managedUserIds,
        visibleDepartmentIds,
        cursorTime,
        cursorId,
        limit);
  }

  @Override
  public List<Candidate> findCandidates(
      String query,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      boolean readyOnly,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findCandidates(
        query,
        actorId,
        scopeAll,
        managedUserIds,
        visibleDepartmentIds,
        readyOnly,
        cursorTime,
        cursorId,
        limit);
  }

  @Override
  public List<Candidate> findCandidatesByExactName(
      String name,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds) {
    return baseMapper.findCandidatesByExactName(
        name, actorId, scopeAll, managedUserIds, visibleDepartmentIds);
  }

  @Override
  public int deleteDuplicateMergedIdentities(String survivorId, String mergedId) {
    return baseMapper.deleteDuplicateMergedIdentities(survivorId, mergedId);
  }

  @Override
  public int findMaxResumeVersion(String resumeId) {
    return baseMapper.findMaxResumeVersion(resumeId);
  }

  @Override
  public ResumeVersion findCandidateResumeVersion(String candidateId, String resumeVersionId) {
    return baseMapper.findCandidateResumeVersion(candidateId, resumeVersionId);
  }

  @Override
  public List<ResumeVersion> findResumeVersions(
      String candidateId, Integer cursorVersionNo, String cursorId, int limit) {
    return baseMapper.findResumeVersions(candidateId, cursorVersionNo, cursorId, limit);
  }

  @Override
  public int moveResumeVersions(String survivorResumeId, String mergedResumeId, int offset) {
    return baseMapper.moveResumeVersions(survivorResumeId, mergedResumeId, offset);
  }

  @Override
  public List<IdentityConflict> findConflicts(
      String status,
      List<String> intakeIds,
      String candidateId,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findConflicts(
        status,
        intakeIds,
        candidateId,
        actorId,
        scopeAll,
        managedUserIds,
        visibleDepartmentIds,
        cursorTime,
        cursorId,
        limit);
  }

  @Override
  public int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now) {
    return baseMapper.insertIdempotency(actorId, key, action, fingerprint, targetId, now);
  }
}
