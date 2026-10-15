package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScreeningSelectionQuery;
import com.ezaigc.hr.intelligence.matching.mapper.ScreeningAdmissionMapper;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ScreeningAdmissionPersistenceServiceImpl
    extends ServiceImpl<ScreeningAdmissionMapper, ScreeningAdmission>
    implements ScreeningAdmissionPersistenceService {

  @Override
  public boolean hasActiveAdmissions(List<String> jdVersionIds) {
    return !jdVersionIds.isEmpty() && baseMapper.exists(Wrappers.<ScreeningAdmission>lambdaQuery()
        .in(ScreeningAdmission::getJdVersionId, jdVersionIds)
        .isNull(ScreeningAdmission::getReturnedAt));
  }

  @Override
  public boolean hasMergeProtectedHistory(String candidateId) {
    return baseMapper.findMergeSelection(candidateId) != null
        || baseMapper.findMergeOutcome(candidateId) != null
        || baseMapper.findMergeWeightedResult(candidateId) != null
        || baseMapper.findMergeAdmission(candidateId) != null;
  }
  @Override
  public List<ScreeningCandidateRow> findSelectionCandidates(ScreeningSelectionQuery query) {
    return baseMapper.findSelectionCandidates(query);
  }

  @Override
  public Set<String> activeResumeVersionIds(List<String> candidateIds) {
    return candidateIds.isEmpty() ? Set.of() : baseMapper.activeResumeVersionIds(candidateIds);
  }

  @Override
  public ScreeningAdmission lockAdmission(String id) {
    return baseMapper.lockAdmission(id);
  }

  @Override
  public int returnToResumes(String id, long version, String actorId, Instant now) {
    return baseMapper.returnToResumes(id, version, actorId, now);
  }

  @Override
  public int completeActive(
      String candidateId, String resumeVersionId, String actorId, Instant now) {
    return baseMapper.completeActive(candidateId, resumeVersionId, actorId, now);
  }

  @Override
  public int readmit(String id, long version, String runId, String actorId, String departmentId, Instant now) {
    return baseMapper.readmit(id, version, runId, actorId, departmentId, now);
  }
  @Override
  public int insertIgnore(String id, String candidateId, String resumeVersionId, String jdVersionId,
      String runId, String actorId, String departmentId, Instant now) {
    return baseMapper.insertIgnore(id, candidateId, resumeVersionId, jdVersionId, runId,
        actorId, departmentId, now);
  }

  @Override
  public int insertBatchIgnore(List<ScreeningAdmission> items) {
    return items.isEmpty() ? 0 : baseMapper.insertBatchIgnore(items);
  }

  @Override
  public ScreeningAdmission findByIdentity(
      String candidateId, String resumeVersionId, String jdVersionId) {
    return baseMapper.findByIdentity(candidateId, resumeVersionId, jdVersionId);
  }

  @Override
  public List<ScreeningCandidateRow> findScreeningCandidates(
      List<String> candidateIds,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      boolean admittedOnly) {
    if (!admittedOnly && candidateIds.isEmpty()) return List.of();
    return baseMapper.findScreeningCandidates(candidateIds, actorId, scopeAll, managedUserIds,
        visibleDepartmentIds, admittedOnly);
  }
}
