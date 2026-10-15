package com.ezaigc.hr.identity.service;

import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import java.time.Instant;
import java.util.List;

/**
 * 身份管理显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * SQL operations that require joins, locks, audit inserts, or guarded writes.
 */
public interface IdentityAdministrationSqlOperations {
  /** 查询部门列表。 */
  List<DepartmentRecord> findDepartments();

  DepartmentRecord findDepartmentById(String id);

  /** 查询用户列表。 */
  List<AdminUserRecord> findUsers(String query, String status, int limit);

  /** 按账号 ID 查询单个管理视图。 */
  AdminUserRecord findUserById(String id);
  /** 按去重账号 ID 批量读取管理视图。 */
  List<AdminUserRecord> findUsersByIds(List<String> ids);

  /** 按角色查询用户列表。 */
  List<AdminUserRecord> findUsersByRole(String roleCode);

  int upsertAssignmentQueue(String departmentId, String ownerUserId, String actorId, Instant now);

  List<String> findActiveSuperAdminUserIdsForUpdate();

  /** 删除会话列表操作人安全主体名称。 */
  int deleteSessionsByPrincipalName(String principalName);

  /** 按来源查询交接列表。 */
  List<HandoverBatchRecord> findHandoversBySource(String sourceUserId);

  /** 完成交接批次。 */
  int completeHandoverBatch(
      String id, String status, int succeeded, int failed, int skipped, Instant now);
}
