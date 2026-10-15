package com.ezaigc.hr.identity.service.impl;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SUPPORT_DEPARTMENTS_PER_USER;
import static com.ezaigc.hr.identity.constant.IdentityConstants.USER_ORGANIZATION_WRITE_BATCH_SIZE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.UserSupportDepartment;
import com.ezaigc.hr.identity.mapper.UserSupportDepartmentMapper;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** UserSupportDepartment 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class UserSupportDepartmentPersistenceServiceImpl
    extends ServiceImpl<UserSupportDepartmentMapper, UserSupportDepartment>
    implements UserSupportDepartmentPersistenceService {

  @Override
  public boolean insertSupportDepartments(
      String userId, Set<String> departmentIds, String actorId, Instant now) {
    if (userId == null
        || departmentIds == null
        || departmentIds.size() > MAX_SUPPORT_DEPARTMENTS_PER_USER
        || departmentIds.stream().anyMatch(Objects::isNull)
        || actorId == null
        || now == null) {
      throw new IllegalArgumentException("identity support department batch is invalid");
    }
    if (departmentIds.isEmpty()) {
      return true;
    }
    List<UserSupportDepartment> supportDepartments =
        departmentIds.stream()
            .map(departmentId -> supportDepartment(userId, departmentId, actorId, now))
            .toList();
    return saveBatch(supportDepartments, USER_ORGANIZATION_WRITE_BATCH_SIZE);
  }

  @Override
  public List<String> findSupportDepartmentIds(String userId) {
    return baseMapper.selectObjs(
        Wrappers.lambdaQuery(UserSupportDepartment.class)
            .select(UserSupportDepartment::getDepartmentId)
            .eq(UserSupportDepartment::getUserId, userId)
            .orderByAsc(UserSupportDepartment::getDepartmentId));
  }

  @Override
  public int deleteSupportDepartments(String userId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(UserSupportDepartment.class)
            .eq(UserSupportDepartment::getUserId, userId));
  }

  private static UserSupportDepartment supportDepartment(
      String userId, String departmentId, String actorId, Instant now) {
    UserSupportDepartment supportDepartment = new UserSupportDepartment();
    supportDepartment.setUserId(userId);
    supportDepartment.setDepartmentId(departmentId);
    supportDepartment.setCreateId(actorId);
    supportDepartment.setCreateTime(now);
    return supportDepartment;
  }
}
