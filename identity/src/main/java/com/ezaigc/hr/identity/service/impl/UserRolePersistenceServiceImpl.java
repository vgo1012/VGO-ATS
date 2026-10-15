package com.ezaigc.hr.identity.service.impl;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ASSIGNABLE_ROLES_PER_USER;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.UserRole;
import com.ezaigc.hr.identity.mapper.UserRoleMapper;
import com.ezaigc.hr.identity.service.UserRolePersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** UserRole 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class UserRolePersistenceServiceImpl extends ServiceImpl<UserRoleMapper, UserRole>
    implements UserRolePersistenceService {

  /** 新增用户角色。 */
  @Override
  public int insertUserRole(
      String userId, String roleId, String actorId, String reason, Instant now) {
    return baseMapper.insert(userRole(userId, roleId, actorId, reason, now));
  }

  @Override
  public boolean insertUserRoles(
      String userId, Set<String> roleIds, String actorId, String reason, Instant now) {
    if (userId == null
        || userId.isBlank()
        || roleIds == null
        || roleIds.isEmpty()
        || roleIds.size() > MAX_ASSIGNABLE_ROLES_PER_USER
        || roleIds.stream().anyMatch(roleId -> roleId == null || roleId.isBlank())
        || actorId == null
        || actorId.isBlank()
        || reason == null
        || reason.isBlank()
        || now == null) {
      throw new IllegalArgumentException("identity user role batch is invalid");
    }
    List<UserRole> userRoles =
        roleIds.stream().map(roleId -> userRole(userId, roleId, actorId, reason, now)).toList();
    return saveBatch(userRoles, MAX_ASSIGNABLE_ROLES_PER_USER);
  }

  /** 删除用户角色。 */
  @Override
  public int deleteUserRole(String userId, String roleId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(UserRole.class)
            .eq(UserRole::getUserId, userId)
            .eq(UserRole::getRoleId, roleId));
  }

  private static UserRole userRole(
      String userId, String roleId, String actorId, String reason, Instant now) {
    UserRole userRole = new UserRole();
    userRole.setUserId(userId);
    userRole.setRoleId(roleId);
    userRole.setAssignedByUserId(actorId);
    userRole.setAssignedAt(now);
    userRole.setReason(reason);
    userRole.setVersion(0L);
    return userRole;
  }
}
