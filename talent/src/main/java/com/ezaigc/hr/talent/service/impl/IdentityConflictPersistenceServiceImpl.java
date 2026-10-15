package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.CandidateConflictCountRow;
import com.ezaigc.hr.talent.enums.IdentityConflictStatus;
import com.ezaigc.hr.talent.mapper.IdentityConflictMapper;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** IdentityConflict 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class IdentityConflictPersistenceServiceImpl
    extends ServiceImpl<IdentityConflictMapper, IdentityConflict>
    implements IdentityConflictPersistenceService {
  @Override
  public String lockCandidateForConflict(String candidateId) {
    return baseMapper.lockCandidateForConflict(candidateId);
  }

  @Override
  public ResumeIntake lockIntake(String intakeId) {
    return baseMapper.lockIntake(intakeId);
  }


  /** 新增冲突。 */
  @Override
  public int insertConflict(
      String id, String intakeId, String candidateA, String candidateB, String type, Instant now) {
    IdentityConflict conflict = new IdentityConflict();
    conflict.setId(id);
    conflict.setIntakeId(intakeId);
    conflict.setCandidateIdA(candidateA);
    conflict.setCandidateIdB(candidateB);
    conflict.setConflictType(type);
    conflict.setStatus(IdentityConflictStatus.OPEN.getCode());
    conflict.setCreateTime(now);
    return baseMapper.insert(conflict);
  }

  /** 查询打开冲突用于接收。 */
  @Override
  public IdentityConflict findOpenConflictForIntake(String intakeId) {
    var conflicts = baseMapper.selectPage(new Page<IdentityConflict>(1, 1, false),
        Wrappers.lambdaQuery(IdentityConflict.class)
            .eq(IdentityConflict::getIntakeId, intakeId)
            .eq(IdentityConflict::getStatus, IdentityConflictStatus.OPEN.getCode())
            .orderByDesc(IdentityConflict::getCreateTime, IdentityConflict::getId)).getRecords();
    return conflicts.isEmpty() ? null : conflicts.getFirst();
  }

  /** 查询冲突。 */
  @Override
  public IdentityConflict findConflict(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询解决冲突用于接收。 */
  @Override
  public IdentityConflict findResolvedConflictForIntake(String intakeId) {
    var conflicts = baseMapper.selectPage(new Page<IdentityConflict>(1, 1, false),
        Wrappers.lambdaQuery(IdentityConflict.class)
            .eq(IdentityConflict::getIntakeId, intakeId)
            .eq(IdentityConflict::getStatus, IdentityConflictStatus.RESOLVED.getCode())
            .orderByDesc(IdentityConflict::getResolvedAt, IdentityConflict::getId)).getRecords();
    return conflicts.isEmpty() ? null : conflicts.getFirst();
  }

  @Override
  public List<CandidateConflictCountRow> countOpenConflictsByCandidateIds(
      Collection<String> candidateIds, String actorId, boolean scopeAll,
      Set<String> managedUserIds, Set<String> visibleDepartmentIds) {
    return candidateIds.isEmpty()
        ? List.of()
        : baseMapper.countOpenByCandidateIds(
            candidateIds, IdentityConflictStatus.Codes.OPEN, actorId, scopeAll,
            managedUserIds, visibleDepartmentIds);
  }

  /** 解析并确定冲突。 */
  @Override
  public int resolveConflict(
      String id,
      String resolution,
      String chosenCandidateId,
      String reason,
      String actorId,
      Instant now) {
    IdentityConflict conflict = new IdentityConflict();
    conflict.setStatus(IdentityConflictStatus.RESOLVED.getCode());
    conflict.setResolution(resolution);
    conflict.setChosenCandidateId(chosenCandidateId);
    conflict.setReason(reason);
    conflict.setResolvedByUserId(actorId);
    conflict.setResolvedAt(now);
    return baseMapper.update(
        conflict,
        Wrappers.lambdaUpdate(IdentityConflict.class)
            .eq(IdentityConflict::getId, id)
            .eq(IdentityConflict::getStatus, IdentityConflictStatus.OPEN.getCode()));
  }
}
