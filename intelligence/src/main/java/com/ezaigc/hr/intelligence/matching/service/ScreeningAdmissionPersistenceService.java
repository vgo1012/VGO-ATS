package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScreeningSelectionQuery;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 智能筛选人工准入持久化边界。 */
public interface ScreeningAdmissionPersistenceService extends IService<ScreeningAdmission> {
  boolean hasActiveAdmissions(List<String> jdVersionIds);


  /** 调用方持有候选人行锁；包含当前及终态历史。 */
  boolean hasMergeProtectedHistory(String candidateId);
  List<ScreeningCandidateRow> findSelectionCandidates(ScreeningSelectionQuery query);

  Set<String> activeResumeVersionIds(List<String> candidateIds);

  ScreeningAdmission lockAdmission(String id);

  int returnToResumes(String id, long version, String actorId, Instant now);

  int completeActive(String candidateId, String resumeVersionId, String actorId, Instant now);

  int readmit(String id, long version, String runId, String actorId, String departmentId, Instant now);
  int insertIgnore(String id, String candidateId, String resumeVersionId, String jdVersionId,
      String runId, String actorId, String departmentId, Instant now);

  int insertBatchIgnore(List<ScreeningAdmission> items);

  ScreeningAdmission findByIdentity(String candidateId, String resumeVersionId, String jdVersionId);

  List<ScreeningCandidateRow> findScreeningCandidates(
      List<String> candidateIds,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      boolean admittedOnly);
}
