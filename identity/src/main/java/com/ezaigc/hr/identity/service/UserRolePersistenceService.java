package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.UserRole;
import java.time.Instant;
import java.util.Set;

/** UserRole 实体的持久化服务。 */
public interface UserRolePersistenceService extends IService<UserRole> {

  /** 新增用户角色。 */
  int insertUserRole(String userId, String roleId, String actorId, String reason, Instant now);

  /** 批量写入账号初始化时的普通角色关系。 */
  boolean insertUserRoles(
      String userId, Set<String> roleIds, String actorId, String reason, Instant now);

  /** 删除用户角色。 */
  int deleteUserRole(String userId, String roleId);
}
