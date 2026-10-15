package com.ezaigc.hr.identity.api;

import java.util.Map;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

public interface IdentityDirectoryService {

  /** 单次目录批量查询上限，防止生成过大的 IN 条件。 */
  int MAX_ACTIVE_USER_BATCH_SIZE = 100;

  UserReference activeUser(String userId);

  /** 加载并校验有效部门，供业务模块校验归属；不授予部门管理能力。 */
  DepartmentReference activeDepartment(String departmentId);

  /** 是否为启用且可承担招聘业务归属的账号；面试协作账号不能成为 JD 归属人。 */
  boolean isRecruiterOwner(String userId);

  /**
   * 批量加载并校验在用账号；任一账号不存在或已停用时整体失败。
   *
   * @param userIds 待校验的平台用户 ID，最多 100 个
   * @return 按用户 ID 索引的不可变目录快照
   */
  Map<String, UserReference> activeUsers(Set<String> userIds);

  /** 只读取姓名，包含停用账号；缺失账号不返回，用于已授权对象的历史归属展示。 */
  Map<String, String> userNames(Set<String> userIds);

  /** 只读取部门名称，包含停用部门；缺失部门不返回，最多 100 个 ID。 */
  Map<String, String> departmentNames(Set<String> departmentIds);

  @Value
  @Accessors(fluent = true)
  class UserReference {
    String userId;
    String displayName;
    String homeDepartmentId;
  }

  @Value
  @Accessors(fluent = true)
  class DepartmentReference {
    String departmentId;
    String code;
    String name;
    String parentDepartmentId;
  }
}
