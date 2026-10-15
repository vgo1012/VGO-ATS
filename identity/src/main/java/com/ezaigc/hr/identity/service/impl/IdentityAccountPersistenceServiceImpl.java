package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 身份账户持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin MyBatis-Plus persistence implementation.
 */
@Service
public class IdentityAccountPersistenceServiceImpl
    extends ServiceImpl<IdentityAccountMapper, UserAccountRecord>
    implements IdentityAccountPersistenceService {

  /** 查询操作人规范化邮箱。 */
  @Override
  public UserAccountRecord findByNormalizedEmail(String normalizedEmail) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(UserAccountRecord.class)
            .eq(UserAccountRecord::getNormalizedEmail, normalizedEmail));
  }

  @Override
  public UserAccountRecord findById(String userId) {
    return baseMapper.selectById(userId);
  }

  @Override
  public UserAccountRecord lockLoginAccount(String normalizedEmail) {
    return baseMapper.lockLoginAccount(normalizedEmail);
  }

  @Override
  public boolean isUsableBootstrappedSuperAdmin(String userId) {
    return baseMapper.isUsableBootstrappedSuperAdmin(userId);
  }

  @Override
  public boolean isFallbackEmergencySuperAdmin(String userId) {
    return baseMapper.isFallbackEmergencySuperAdmin(
        userId, IdentityActivationStatus.ACTIVE.getCode());
  }

  @Override
  public int updateLoginFailure(String userId, int count, Instant windowStartedAt,
      Instant lockedUntil) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(UserAccountRecord.class)
            .set(UserAccountRecord::getFailedLoginCount, count)
            .set(UserAccountRecord::getFailedLoginWindowStartedAt, windowStartedAt)
            .set(UserAccountRecord::getLoginLockedUntil, lockedUntil)
            .eq(UserAccountRecord::getId, userId));
  }

  @Override
  public int clearLoginFailures(String userId) {
    return updateLoginFailure(userId, 0, null, null);
  }

  @Override
  public int advanceSessionVersion(String userId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(UserAccountRecord.class)
            .setIncrBy(UserAccountRecord::getVersion, 1)
            .set(UserAccountRecord::getUpdateId, actorId)
            .set(UserAccountRecord::getUpdateTime, now)
            .eq(UserAccountRecord::getId, userId)
            .eq(UserAccountRecord::getStatus, IdentityActivationStatus.ACTIVE.getCode()));
  }

  @Override
  public List<UserAccountRecord> findDirectoryUsers(String query, String afterId, int limit) {
    return baseMapper.findDirectoryUsers(query, afterId, limit, IdentityActivationStatus.ACTIVE.getCode())
        .stream().map(row -> {
      UserAccountRecord user = new UserAccountRecord();
      user.setId(row.getId());
      user.setDisplayName(row.getDisplayName());
      user.setHomeDepartmentId(row.getHomeDepartmentId());
      user.setStatus(row.getStatus());
      return user;
    }).toList();
  }

  @Override
  public List<UserAccountRecord> resolveDirectoryUsers(Set<String> ids) {
    if (ids.isEmpty()) return List.of();
    return baseMapper.selectList(
        Wrappers.lambdaQuery(UserAccountRecord.class)
            .select(UserAccountRecord::getId, UserAccountRecord::getDisplayName,
                UserAccountRecord::getHomeDepartmentId, UserAccountRecord::getStatus)
            .in(UserAccountRecord::getId, ids)
            .orderByAsc(UserAccountRecord::getId));
  }

  @Override
  public String findDirectLeaderId(String userId) {
    UserAccountRecord account = baseMapper.selectById(userId);
    return account == null ? null : account.getDirectLeaderUserId();
  }

  /** 统计用于唯一匹配的规范化邮箱。 */
  @Override
  public int countNormalizedEmail(String normalizedEmail) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(UserAccountRecord.class)
                .eq(UserAccountRecord::getNormalizedEmail, normalizedEmail)));
  }

  @Override
  public int countActiveUsersInDepartment(String departmentId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(UserAccountRecord.class)
                .eq(UserAccountRecord::getHomeDepartmentId, departmentId)
                .eq(UserAccountRecord::getStatus, IdentityActivationStatus.ACTIVE.getCode())));
  }

  /** 新增用户。 */
  @Override
  public int insertUser(
      String id,
      String displayName,
      String workEmail,
      String normalizedEmail,
      String credentialHash,
      String sourceTitle,
      String departmentId,
      String leaderId,
      String actorId,
      Instant now) {
    UserAccountRecord account = new UserAccountRecord();
    account.setId(id);
    account.setDisplayName(displayName);
    account.setWorkEmail(workEmail);
    account.setNormalizedEmail(normalizedEmail);
    account.setCredentialHash(credentialHash);
    account.setSourceTitle(sourceTitle);
    account.setHomeDepartmentId(departmentId);
    account.setDirectLeaderUserId(leaderId);
    account.setStatus(IdentityActivationStatus.ACTIVE.getCode());
    account.setMustChangePassword(true);
    account.setRegisteredAt(now);
    account.setVersion(0L);
    account.setCreateId(actorId);
    account.setCreateTime(now);
    account.setUpdateId(actorId);
    account.setUpdateTime(now);
    return baseMapper.insert(account);
  }

  /** 更新用户。 */
  @Override
  public int updateUser(
      String id,
      String displayName,
      String workEmail,
      String normalizedEmail,
      String sourceTitle,
      String departmentId,
      String leaderId,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(UserAccountRecord.class)
            .set(UserAccountRecord::getDisplayName, displayName)
            .set(UserAccountRecord::getWorkEmail, workEmail)
            .set(UserAccountRecord::getNormalizedEmail, normalizedEmail)
            .set(UserAccountRecord::getSourceTitle, sourceTitle)
            .set(UserAccountRecord::getHomeDepartmentId, departmentId)
            .set(UserAccountRecord::getDirectLeaderUserId, leaderId)
            .set(UserAccountRecord::getUpdateId, actorId)
            .set(UserAccountRecord::getUpdateTime, now)
            .eq(UserAccountRecord::getId, id)
            .eq(UserAccountRecord::getVersion, version)
            .setIncrBy(UserAccountRecord::getVersion, 1)
            .eq(UserAccountRecord::getStatus, IdentityActivationStatus.ACTIVE.getCode()));
  }

  /** 更新凭据。 */
  @Override
  public int updateCredential(
      String id, String credentialHash, boolean mustChange, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(UserAccountRecord.class)
            .set(UserAccountRecord::getCredentialHash, credentialHash)
            .set(UserAccountRecord::isMustChangePassword, mustChange)
            .set(UserAccountRecord::getCredentialResetAt, now)
            .set(UserAccountRecord::getFailedLoginCount, 0)
            .set(UserAccountRecord::getFailedLoginWindowStartedAt, null)
            .set(UserAccountRecord::getLoginLockedUntil, null)
            .setIncrBy(UserAccountRecord::getVersion, 1)
            .set(UserAccountRecord::getUpdateId, actorId)
            .set(UserAccountRecord::getUpdateTime, now)
            .eq(UserAccountRecord::getId, id)
            .eq(UserAccountRecord::getStatus, IdentityActivationStatus.ACTIVE.getCode()));
  }

  /** 停用用户。 */
  @Override
  public int disableUser(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(UserAccountRecord.class)
            .set(UserAccountRecord::getStatus, IdentityActivationStatus.DISABLED.getCode())
            .set(UserAccountRecord::getDisabledAt, now)
            .set(UserAccountRecord::getUpdateId, actorId)
            .set(UserAccountRecord::getUpdateTime, now)
            .eq(UserAccountRecord::getId, id)
            .eq(UserAccountRecord::getVersion, version)
            .setIncrBy(UserAccountRecord::getVersion, 1)
            .eq(UserAccountRecord::getStatus, IdentityActivationStatus.ACTIVE.getCode()));
  }

  /** 逻辑删除用户。 */
  @Override
  public int deleteUser(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(UserAccountRecord.class)
            .set(UserAccountRecord::getStatus, IdentityActivationStatus.DELETED.getCode())
            .set(UserAccountRecord::getDisabledAt, now)
            .set(UserAccountRecord::getUpdateId, actorId)
            .set(UserAccountRecord::getUpdateTime, now)
            .eq(UserAccountRecord::getId, id)
            .eq(UserAccountRecord::getVersion, version)
            .setIncrBy(UserAccountRecord::getVersion, 1)
            .ne(UserAccountRecord::getStatus, IdentityActivationStatus.DELETED.getCode()));
  }

  @Override
  public List<String> findRoleCodes(String userId) {
    return baseMapper.findRoleCodes(userId);
  }

  @Override
  public List<String> findPermissionCodes(String userId) {
    return baseMapper.findPermissionCodes(userId);
  }

  @Override
  public List<String> findManagedUserIds(String userId) {
    return baseMapper.findManagedUserIds(userId);
  }

  @Override
  public List<String> findReportingManagedUserIds(String userId) {
    return baseMapper.findReportingManagedUserIds(userId);
  }
}
