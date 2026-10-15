package com.ezaigc.hr.identity.service;

import java.util.List;

/**
 * 身份账户显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * <p>Account authentication reads that require projections or role joins.
 */
public interface IdentityAccountSqlOperations {

  /** 查询角色编码列表。 */
  List<String> findRoleCodes(String userId);

  /** 查询权限编码列表。 */
  List<String> findPermissionCodes(String userId);

  /** 查询当前用户的完整下属树。 */
  List<String> findManagedUserIds(String userId);

  /** 报表专用的当前完整管理链，包含停用账号和停用中间节点的下属。 */
  List<String> findReportingManagedUserIds(String userId);
}
