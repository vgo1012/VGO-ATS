package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import com.ezaigc.hr.intelligence.matching.enums.WeightProfileStatus;
import com.ezaigc.hr.intelligence.matching.mapper.WeightProfileMapper;
import com.ezaigc.hr.intelligence.matching.service.WeightProfilePersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** WeightProfile 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class WeightProfilePersistenceServiceImpl
    extends ServiceImpl<WeightProfileMapper, WeightProfile>
    implements WeightProfilePersistenceService {

  /** 查询权重配置方案列表。 */
  @Override
  public List<WeightProfile> findWeightProfiles(
      String ownerUserId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    var query =
        Wrappers.lambdaQuery(WeightProfile.class)
            .and(
                !scopeAll,
                scope -> {
                  scope.eq(WeightProfile::getOwnerUserId, ownerUserId);
                  if (!managedUserIds.isEmpty()) {
                    scope.or().in(WeightProfile::getOwnerUserId, managedUserIds);
                  }
                })
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(WeightProfile::getUpdateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(WeightProfile::getUpdateTime, cursorTime)
                                    .lt(WeightProfile::getId, cursorId)))
            .orderByDesc(WeightProfile::getUpdateTime, WeightProfile::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  @Override
  public long countWeightProfiles(
      String ownerUserId, boolean scopeAll, Set<String> managedUserIds) {
    return baseMapper.selectCount(
        Wrappers.lambdaQuery(WeightProfile.class)
            .and(
                !scopeAll,
                scope -> {
                  scope.eq(WeightProfile::getOwnerUserId, ownerUserId);
                  if (!managedUserIds.isEmpty()) {
                    scope.or().in(WeightProfile::getOwnerUserId, managedUserIds);
                  }
                }));
  }

  /** 查询权重配置方案。 */
  @Override
  public WeightProfile findWeightProfile(String id) {
    return baseMapper.selectById(id);
  }

  /** 新增权重配置方案。 */
  @Override
  public int insertWeightProfile(String id, String ownerUserId, String name, Instant now) {
    WeightProfile profile = new WeightProfile();
    profile.setId(id);
    profile.setOwnerUserId(ownerUserId);
    profile.setName(name);
    profile.setStatus(WeightProfileStatus.ACTIVE.getCode());
    profile.setCurrentVersionNo(1);
    profile.setVersion(0L);
    profile.setCreateId(ownerUserId);
    profile.setCreateTime(now);
    profile.setUpdateId(ownerUserId);
    profile.setUpdateTime(now);
    return baseMapper.insert(profile);
  }

  /** 更新权重配置方案。 */
  @Override
  public int updateWeightProfile(
      String id, String ownerUserId, String name, int versionNo, long version, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(WeightProfile.class)
            .eq(WeightProfile::getId, id)
            .eq(WeightProfile::getOwnerUserId, ownerUserId)
            .eq(WeightProfile::getVersion, version)
            .setIncrBy(WeightProfile::getVersion, 1)
            .eq(WeightProfile::getStatus, WeightProfileStatus.ACTIVE.getCode())
            .set(WeightProfile::getName, name)
            .set(WeightProfile::getCurrentVersionNo, versionNo)
            .set(WeightProfile::getUpdateId, ownerUserId)
            .set(WeightProfile::getUpdateTime, now));
  }

  /** 删除权重配置方案。 */
  @Override
  public int deleteWeightProfile(String id, String ownerUserId, long version) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(WeightProfile.class)
            .eq(WeightProfile::getId, id)
            .eq(WeightProfile::getOwnerUserId, ownerUserId)
            .eq(WeightProfile::getVersion, version));
  }
}
