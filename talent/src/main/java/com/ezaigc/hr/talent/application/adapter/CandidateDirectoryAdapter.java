package com.ezaigc.hr.talent.application.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.application.manager.CandidateReadAccess;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateDirectoryAdapter implements CandidateDirectoryService, CandidateAccessPolicy {

  @Resource private CandidatePersistenceService candidatePersistenceService;
  @Resource private CandidateReadAccess candidateReadAccess;

  @Override
  public Map<String, String> namesForAuthorizedCandidates(Collection<String> candidateIds) {
    if (candidateIds == null || candidateIds.size() > 200) {
      throw new IllegalArgumentException("候选人姓名批量查询最多200项");
    }
    if (candidateIds.isEmpty()) return Map.of();
    var candidates = candidatePersistenceService.list(Wrappers.<Candidate>lambdaQuery()
        .select(Candidate::getId, Candidate::getFullName, Candidate::getStatus)
        .in(Candidate::getId, candidateIds)
        .eq(Candidate::getStatus, CandidateStatus.ACTIVE.getCode()));
    Map<String, String> names = new HashMap<>();
    candidates.stream().filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus()))
        .forEach(candidate -> names.put(candidate.getId(), candidate.getFullName()));
    return names;
  }

  @Override
  public Set<String> factReadableCandidateIds(Collection<String> candidateIds) {
    if (candidateIds.size() > 1000) throw new IllegalArgumentException("候选人批量查询最多1000项");
    if (candidateIds.isEmpty()) return Set.of();
    var rows = candidatePersistenceService.listByIds(candidateIds).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus()))
        .toList();
    return candidateReadAccess.evaluate(rows).getReadableCandidateIds();
  }

  @Override
  public Set<String> ownedCandidateIds(Collection<String> candidateIds, String ownerUserId) {
    if (candidateIds.size() > 1000) throw new IllegalArgumentException("候选人批量查询最多1000项");
    if (candidateIds.isEmpty()) return Set.of();
    return candidatePersistenceService.listByIds(candidateIds).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus()))
        .filter(candidate -> ownerUserId.equals(candidate.getOwnerUserId()))
        .map(Candidate::getId).collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public Set<String> matchingCandidateIds(Collection<String> candidateIds, String query) {
    if (candidateIds.size() > 1000) throw new IllegalArgumentException("候选人批量查询最多1000项");
    if (candidateIds.isEmpty()) return Set.of();
    String keyword = query.strip().toLowerCase(Locale.ROOT);
    var rows = candidatePersistenceService.listByIds(candidateIds).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus())).toList();
    var access = candidateReadAccess.evaluate(rows);
    return rows.stream().filter(candidate -> access.canReadCandidate(candidate.getId()))
        .filter(candidate -> contains(candidate.getFullName(), keyword)
            || contains(candidate.getPrimaryEmail(), keyword) || contains(candidate.getPrimaryPhone(), keyword))
        .map(Candidate::getId).collect(Collectors.toSet());
  }

  private static boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
  }

  @Override
  public CandidateRef requireActive(String candidateId) {
    var candidate = activeCandidate(candidateId);
    return new CandidateRef(candidate.getId(), candidate.getFullName());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public CandidateRef lockActive(String candidateId) {
    var candidate = candidatePersistenceService.findCandidateForUpdate(candidateId);
    if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
      throw new PlatformException(HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", "候选人不存在或已停用", false);
    }
    return new CandidateRef(candidate.getId(), candidate.getFullName());
  }

  @Override
  public void requireReadable(String candidateId) {
    candidateReadAccess.requireCandidate(activeCandidate(candidateId));
  }

  @Override
  public void requireAllResumesReadable(String candidateId) {
    candidateReadAccess.requireAllResumes(activeCandidate(candidateId));
  }

  @Override
  public void requireResumeReadable(String candidateId, String resumeVersionId) {
    candidateReadAccess.requireResume(activeCandidate(candidateId), resumeVersionId);
  }

  @Override
  public Set<String> readableResumeVersionIds(Map<String, String> resumeVersionToCandidate) {
    if (resumeVersionToCandidate.size() > 1000) {
      throw new IllegalArgumentException("简历版本批量鉴权最多1000项");
    }
    if (resumeVersionToCandidate.isEmpty()) return Set.of();
    var candidates = candidatePersistenceService
        .listByIds(new LinkedHashSet<>(resumeVersionToCandidate.values())).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus()))
        .toList();
    var access = candidateReadAccess.evaluate(candidates);
    return resumeVersionToCandidate.entrySet().stream()
        .filter(entry -> access.canReadResume(entry.getValue(), entry.getKey()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Candidate activeCandidate(String candidateId) {
    var candidate = candidatePersistenceService.findCandidate(candidateId);
    if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", "候选人不存在或已停用", false);
    }
    return candidate;
  }
}
