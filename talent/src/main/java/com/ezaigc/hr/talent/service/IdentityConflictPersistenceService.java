package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.CandidateConflictCountRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** IdentityConflict 实体的持久化服务。 */
public interface IdentityConflictPersistenceService extends IService<IdentityConflict> {
  String lockCandidateForConflict(String candidateId);

  ResumeIntake lockIntake(String intakeId);


  /** 新增冲突。 */
  int insertConflict(
      String id, String intakeId, String candidateA, String candidateB, String type, Instant now);

  /** 查询打开冲突用于接收。 */
  IdentityConflict findOpenConflictForIntake(String intakeId);

  /** 查询冲突。 */
  IdentityConflict findConflict(String id);

  /** 查询解决冲突用于接收。 */
  IdentityConflict findResolvedConflictForIntake(String intakeId);

  List<CandidateConflictCountRow> countOpenConflictsByCandidateIds(
      Collection<String> candidateIds, String actorId, boolean scopeAll,
      Set<String> managedUserIds, Set<String> visibleDepartmentIds);

  /** 解析并确定冲突。 */
  int resolveConflict(
      String id,
      String resolution,
      String chosenCandidateId,
      String reason,
      String actorId,
      Instant now);
}
