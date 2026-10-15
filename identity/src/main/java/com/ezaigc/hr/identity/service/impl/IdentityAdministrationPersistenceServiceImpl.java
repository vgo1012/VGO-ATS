package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.mapper.IdentityAdministrationMapper;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 身份管理持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin MyBatis-Plus persistence implementation.
 */
@Service
public class IdentityAdministrationPersistenceServiceImpl
    extends ServiceImpl<IdentityAdministrationMapper, DepartmentRecord>
    implements IdentityAdministrationPersistenceService {
  /** 统计部门编码。 */
  @Override
  public int countDepartmentCode(String code) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(DepartmentRecord.class).eq(DepartmentRecord::getCode, code)));
  }

  /** 新增部门。 */
  @Override
  public int insertDepartment(
      String id, String code, String name, String parentId, String actorId, Instant now) {
    DepartmentRecord department = new DepartmentRecord();
    department.setId(id);
    department.setCode(code);
    department.setName(name);
    department.setParentDepartmentId(parentId);
    department.setStatus(IdentityActivationStatus.ACTIVE.getCode());
    department.setVersion(0L);
    department.setCreateId(actorId);
    department.setCreateTime(now);
    department.setUpdateId(actorId);
    department.setUpdateTime(now);
    return baseMapper.insert(department);
  }

  /** 更新部门。 */
  @Override
  public int updateDepartment(
      String id,
      String name,
      String parentId,
      String status,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(DepartmentRecord.class)
            .set(DepartmentRecord::getName, name)
            .set(DepartmentRecord::getParentDepartmentId, parentId)
            .set(DepartmentRecord::getStatus, status)
            .set(DepartmentRecord::getUpdateId, actorId)
            .set(DepartmentRecord::getUpdateTime, now)
            .eq(DepartmentRecord::getId, id)
            .eq(DepartmentRecord::getVersion, version)
            .setIncrBy(DepartmentRecord::getVersion, 1));
  }

  @Override
  public int countActiveChildDepartments(String departmentId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(DepartmentRecord.class)
                .eq(DepartmentRecord::getParentDepartmentId, departmentId)
                .eq(DepartmentRecord::getStatus, IdentityActivationStatus.ACTIVE.getCode())));
  }

  @Override
  public List<DepartmentRecord> findDepartments() {
    return baseMapper.findDepartments();
  }

  @Override
  public DepartmentRecord findDepartmentById(String id) {
    return baseMapper.findDepartmentById(id);
  }

  @Override
  public List<AdminUserRecord> findUsers(String query, String status, int limit) {
    return baseMapper.findUsers(query, status, limit);
  }

  @Override
  public int deleteDepartment(String departmentId, long version) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(DepartmentRecord.class)
            .eq(DepartmentRecord::getId, departmentId)
            .eq(DepartmentRecord::getVersion, version));
  }

  @Override
  public AdminUserRecord findUserById(String id) {
    return baseMapper.findUserById(id);
  }

  @Override
  public List<AdminUserRecord> findUsersByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    List<String> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    List<AdminUserRecord> users = new ArrayList<>();
    for (int start = 0; start < distinctIds.size(); start += 500) {
      users.addAll(baseMapper.findUsersByIds(distinctIds.subList(start, Math.min(start + 500, distinctIds.size()))));
    }
    return List.copyOf(users);
  }

  @Override
  public List<AdminUserRecord> findUsersByRole(String roleCode) {
    return baseMapper.findUsersByRole(roleCode);
  }

  @Override
  public int upsertAssignmentQueue(
      String departmentId, String ownerUserId, String actorId, Instant now) {
    return baseMapper.upsertAssignmentQueue(departmentId, ownerUserId, actorId, now);
  }

  @Override
  public List<String> findActiveSuperAdminUserIdsForUpdate() {
    return baseMapper.findActiveSuperAdminUserIdsForUpdate();
  }

  @Override
  public int deleteSessionsByPrincipalName(String principalName) {
    return baseMapper.deleteSessionsByPrincipalName(principalName);
  }

  @Override
  public List<HandoverBatchRecord> findHandoversBySource(String sourceUserId) {
    return baseMapper.findHandoversBySource(sourceUserId);
  }

  @Override
  public int completeHandoverBatch(
      String id, String status, int succeeded, int failed, int skipped, Instant now) {
    return baseMapper.completeHandoverBatch(id, status, succeeded, failed, skipped, now);
  }
}
