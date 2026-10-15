package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.enums.EngagementLockStatus;
import com.ezaigc.hr.recruiting.recruitment.mapper.EngagementLockMapper;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 跟进归属锁定持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation; permissions and transactions stay in application managers.
 */
@Service
public class EngagementLockPersistenceServiceImpl
    extends ServiceImpl<EngagementLockMapper, EngagementLock>
    implements EngagementLockPersistenceService {
  /** 查询操作人候选人。 */
  @Override
  public EngagementLock findByCandidate(String candidateId) {
    return getOne(
        Wrappers.lambdaQuery(EngagementLock.class).eq(EngagementLock::getCandidateId, candidateId));
  }

  @Override
  public List<EngagementLock> activeByCandidates(
      Collection<String> candidateIds, Instant now) {
    if (candidateIds == null || candidateIds.isEmpty()) {
      return List.of();
    }
    return list(
        Wrappers.lambdaQuery(EngagementLock.class)
            .in(EngagementLock::getCandidateId, candidateIds)
            .eq(EngagementLock::getStatus, EngagementLockStatus.ACTIVE.getCode())
            .gt(EngagementLock::getExpiresAt, now));
  }

  @Override
  public EngagementLock findById(String id) {
    return getById(id);
  }

  @Override
  public int transfer(
      String id,
      String ownerId,
      String departmentId,
      String contactName,
      String status,
      Instant expiresAt,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(EngagementLock.class)
            .eq(EngagementLock::getId, id)
            .eq(EngagementLock::getVersion, version)
            .setIncrBy(EngagementLock::getVersion, 1)
            .eq(EngagementLock::getStatus, EngagementLockStatus.ACTIVE.getCode())
            .gt(EngagementLock::getExpiresAt, now)
            .set(EngagementLock::getOwnerUserId, ownerId)
            .set(EngagementLock::getOwnerDepartmentId, departmentId)
            .set(EngagementLock::getContactName, contactName)
            .set(EngagementLock::getStatus, status)
            .set(EngagementLock::getExpiresAt, expiresAt)
            .set(EngagementLock::getUpdateId, actorId)
            .set(EngagementLock::getUpdateTime, now));
  }

  @Override
  public int renew(
      String id,
      String progressType,
      Instant expiresAt,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(EngagementLock.class)
            .eq(EngagementLock::getId, id)
            .eq(EngagementLock::getVersion, version)
            .setIncrBy(EngagementLock::getVersion, 1)
            .eq(EngagementLock::getStatus, EngagementLockStatus.ACTIVE.getCode())
            .gt(EngagementLock::getExpiresAt, now)
            .set(EngagementLock::getExpiresAt, expiresAt)
            .set(EngagementLock::getLastProgressType, progressType)
            .set(EngagementLock::getUpdateId, actorId)
            .set(EngagementLock::getUpdateTime, now));
  }

  @Override
  public List<EngagementLock> activeByOwner(String ownerId, Instant now) {
    return list(
        Wrappers.lambdaQuery(EngagementLock.class)
            .eq(EngagementLock::getOwnerUserId, ownerId)
            .eq(EngagementLock::getStatus, EngagementLockStatus.ACTIVE.getCode())
            .gt(EngagementLock::getExpiresAt, now)
            .orderByAsc(EngagementLock::getExpiresAt, EngagementLock::getId));
  }

  @Override
  public List<EngagementLock> due(Instant now, int limit) {
    return page(
            new Page<>(1, limit, false),
            Wrappers.lambdaQuery(EngagementLock.class)
                .eq(EngagementLock::getStatus, EngagementLockStatus.ACTIVE.getCode())
                .le(EngagementLock::getExpiresAt, now)
                .orderByAsc(EngagementLock::getExpiresAt, EngagementLock::getId))
        .getRecords();
  }

  @Override
  public int expire(String id, long version, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(EngagementLock.class)
            .eq(EngagementLock::getId, id)
            .eq(EngagementLock::getVersion, version)
            .setIncrBy(EngagementLock::getVersion, 1)
            .eq(EngagementLock::getStatus, EngagementLockStatus.ACTIVE.getCode())
            .le(EngagementLock::getExpiresAt, now)
            .set(EngagementLock::getStatus, EngagementLockStatus.EXPIRED.getCode())
            .set(EngagementLock::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(EngagementLock::getUpdateTime, now));
  }
}
