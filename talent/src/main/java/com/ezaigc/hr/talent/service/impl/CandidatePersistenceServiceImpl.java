package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.mapper.CandidateMapper;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Candidate 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CandidatePersistenceServiceImpl extends ServiceImpl<CandidateMapper, Candidate>
    implements CandidatePersistenceService {

  @Override
  public boolean hasMergeProtectedHistory(String candidateId) {
    return baseMapper.findMergeBlacklistHistory(candidateId) != null
        || baseMapper.findMergeBlacklistProjection(candidateId) != null
        || baseMapper.findMergeEducation(candidateId) != null
        || baseMapper.findMergeLifecycle(candidateId) != null
        || baseMapper.findMergeScoreBinding(candidateId) != null;
  }
  @Override
  public String lockCandidate(String id) { return baseMapper.lockCandidate(id); }

  @Override
  public Candidate findCandidateForUpdate(String id) { return baseMapper.findCandidateForUpdate(id); }

  @Override
  public int updateLibraryReview(String id, long version, String ownerId, String ext, Instant now) {
    Candidate row = new Candidate();
    row.setVersion(version); row.setExt(ext); row.setUpdateId(ownerId); row.setUpdateTime(now);
    return baseMapper.update(row, Wrappers.lambdaUpdate(Candidate.class)
        .eq(Candidate::getId, id).eq(Candidate::getOwnerUserId, ownerId)
        .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode()));
  }

  @Override
  public int reclaimFromLibrary(
      String id,
      long version,
      String ownerId,
      String departmentId,
      String ext,
      String actorId,
      Instant now) {
    Candidate row = new Candidate();
    row.setVersion(version);
    row.setOwnerUserId(ownerId);
    row.setOwnerDepartmentId(departmentId);
    row.setExt(ext);
    row.setUpdateId(actorId);
    row.setUpdateTime(now);
    return baseMapper.update(
        row,
        Wrappers.lambdaUpdate(Candidate.class)
            .eq(Candidate::getId, id)
            .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode())
            .set(ext == null, Candidate::getExt, null));
  }

  @Override
  public int markReturnedToShared(
      String id, long version, String ext, String actorId, Instant now) {
    Candidate row = new Candidate();
    row.setVersion(version);
    row.setExt(ext);
    row.setUpdateId(actorId);
    row.setUpdateTime(now);
    return baseMapper.update(
        row,
        Wrappers.lambdaUpdate(Candidate.class)
            .eq(Candidate::getId, id)
            .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode()));
  }

  /** 新增候选人。 */
  @Override
  public int insertCandidate(
      String id,
      String name,
      String email,
      String phone,
      String ownerId,
      String departmentId,
      String actorId,
      Instant now) {
    Candidate candidate = new Candidate();
    candidate.setId(id);
    candidate.setFullName(name);
    candidate.setPrimaryEmail(email);
    candidate.setPrimaryPhone(phone);
    candidate.setOwnerUserId(ownerId);
    candidate.setOwnerDepartmentId(departmentId);
    candidate.setStatus(CandidateStatus.ACTIVE.getCode());
    candidate.setCreateId(actorId);
    candidate.setCreateTime(now);
    candidate.setUpdateId(actorId);
    candidate.setUpdateTime(now);
    return baseMapper.insert(candidate);
  }

  /** 查询候选人。 */
  @Override
  public Candidate findCandidate(String id) {
    return baseMapper.selectById(id);
  }

  /** 更新候选人。 */
  @Override
  public int updateCandidate(
      String id,
      String name,
      String email,
      String phone,
      String ownerId,
      String departmentId,
      long version,
      String actorId,
      Instant now) {
    Candidate candidate = new Candidate();
    candidate.setVersion(version);
    candidate.setFullName(name);
    candidate.setPrimaryEmail(email);
    candidate.setPrimaryPhone(phone);
    candidate.setOwnerUserId(ownerId);
    candidate.setOwnerDepartmentId(departmentId);
    candidate.setUpdateId(actorId);
    candidate.setUpdateTime(now);
    return baseMapper.update(
        candidate,
        Wrappers.lambdaUpdate(Candidate.class)
            .eq(Candidate::getId, id)
            .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode())
            .set(email == null, Candidate::getPrimaryEmail, null)
            .set(phone == null, Candidate::getPrimaryPhone, null));
  }

  /** 删除候选人。 */
  @Override
  public int deleteCandidate(String id, long version, String actorId, Instant now) {
    Candidate candidate = new Candidate();
    candidate.setVersion(version);
    candidate.setFullName("已删除候选人");
    candidate.setPrimaryEmail(null);
    candidate.setPrimaryPhone(null);
    candidate.setStatus(CandidateStatus.DELETED.getCode());
    candidate.setUpdateId(actorId);
    candidate.setUpdateTime(now);
    return baseMapper.update(
        candidate,
        Wrappers.lambdaUpdate(Candidate.class)
            .eq(Candidate::getId, id)
            .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode())
            .set(Candidate::getPrimaryEmail, null)
            .set(Candidate::getPrimaryPhone, null));
  }

  /** 标记候选人被合并。 */
  @Override
  public int markCandidateMerged(String id, long version, String actorId, Instant now) {
    Candidate candidate = new Candidate();
    candidate.setVersion(version);
    candidate.setFullName("已合并候选人");
    candidate.setStatus(CandidateStatus.DELETED.getCode());
    candidate.setUpdateId(actorId);
    candidate.setUpdateTime(now);
    return baseMapper.update(
        candidate,
        Wrappers.lambdaUpdate(Candidate.class)
            .eq(Candidate::getId, id)
            .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode())
            .set(Candidate::getPrimaryEmail, null)
            .set(Candidate::getPrimaryPhone, null));
  }
}
