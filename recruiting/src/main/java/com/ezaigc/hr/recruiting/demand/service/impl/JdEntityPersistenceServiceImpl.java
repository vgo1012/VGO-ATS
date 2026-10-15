package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.mapper.JdEntityMapper;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** JdEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class JdEntityPersistenceServiceImpl extends ServiceImpl<JdEntityMapper, JdEntity>
    implements JdEntityPersistenceService {
  @Override
  public String departmentCode(String departmentId) {
    lockCodeAllocation();
    String existing = baseMapper.findDepartmentCode(departmentId);
    if (existing != null) return existing;
    String last = baseMapper.lastDepartmentCode();
    int next = last == null ? 1 : Integer.parseInt(last) + 1;
    if (next > 99) return null;
    String code = String.format(Locale.ROOT, "%02d", next);
    if (baseMapper.insertDepartmentCode(departmentId, code) != 1) {
      throw new IllegalStateException("JD 部门码写入失败");
    }
    return code;
  }

  @Override
  public String nextCode(String departmentCode, String jobTypeCode, String workRegionCode) {
    lockCodeAllocation();
    String pair = String.format(Locale.ROOT, "%02d%03d%03d",
        Integer.parseInt(departmentCode), Integer.parseInt(jobTypeCode), Integer.parseInt(workRegionCode));
    baseMapper.initializePairSequence(pair);
    String code;
    do {
      if (baseMapper.advancePairSequence(pair) != 1) {
        throw new IllegalStateException("JD 组合编号流水已耗尽");
      }
      code = "JD-" + pair + String.format(Locale.ROOT, "%03d", baseMapper.currentPairSequence(pair));
    } while (baseMapper.findUsedCodeForUpdate(code) != null);
    return code;
  }

  @Override
  public void lockCodeAllocation() {
    if (baseMapper.lockCodeAllocation() == null) {
      throw new IllegalStateException("JD 编号序列缺失，请检查数据库升级状态");
    }
  }

  @Override
  public int updateCatalog(JdEntity jd, long expectedVersion) {
    return baseMapper.update(Wrappers.<JdEntity>lambdaUpdate()
        .eq(JdEntity::getId, jd.getId()).eq(JdEntity::getVersion, expectedVersion)
        .eq(JdEntity::isDeleted, false)
        .set(JdEntity::getTitle, jd.getTitle()).set(JdEntity::getClientId, jd.getClientId())
        .set(JdEntity::getRecruitmentType, jd.getRecruitmentType())
        .set(JdEntity::getJobTypeCode, jd.getJobTypeCode())
        .set(JdEntity::getWorkRegionCode, jd.getWorkRegionCode())
        .set(JdEntity::getHeadcount, jd.getHeadcount())
        .set(JdEntity::getRecruitmentCycleDays, jd.getRecruitmentCycleDays())
        .set(JdEntity::getOwnerUserId, jd.getOwnerUserId())
        .set(JdEntity::getOwnerDepartmentId, jd.getOwnerDepartmentId())
        .set(JdEntity::getDistinction, jd.getDistinction()).set(JdEntity::isPublicVisible, jd.isPublicVisible())
        .set(JdEntity::isContentShared, jd.isContentShared())
        .set(JdEntity::getCurrentVersionNo, jd.getCurrentVersionNo())
        .setIncrBy(JdEntity::getVersion, 1)
        .set(JdEntity::getUpdateId, jd.getUpdateId()).set(JdEntity::getUpdateTime, jd.getUpdateTime()));
  }

  @Override
  public int updateRecruitmentMetadata(
      String id,
      int headcount,
      int recruitmentCycleDays,
      String ownerUserId,
      String ownerDepartmentId,
      boolean contentShared,
      long expectedVersion,
      String actorId,
      Instant now) {
    return baseMapper.update(Wrappers.<JdEntity>lambdaUpdate()
        .eq(JdEntity::getId, id).eq(JdEntity::getVersion, expectedVersion)
        .eq(JdEntity::isDeleted, false)
        .set(JdEntity::getHeadcount, headcount)
        .set(JdEntity::getRecruitmentCycleDays, recruitmentCycleDays)
        .set(JdEntity::getOwnerUserId, ownerUserId)
        .set(JdEntity::getOwnerDepartmentId, ownerDepartmentId)
        .set(JdEntity::isContentShared, contentShared)
        .setIncrBy(JdEntity::getVersion, 1)
        .set(JdEntity::getUpdateId, actorId).set(JdEntity::getUpdateTime, now));
  }

  @Override
  public int softDelete(String id, long version, String actorId, Instant now) {
    return baseMapper.update(Wrappers.<JdEntity>lambdaUpdate().eq(JdEntity::getId, id)
        .eq(JdEntity::getVersion, version).eq(JdEntity::isDeleted, false)
        .set(JdEntity::isDeleted, true).setIncrBy(JdEntity::getVersion, 1)
        .set(JdEntity::getClosedByUserId, actorId).set(JdEntity::getClosedAt, now)
        .set(JdEntity::getUpdateId, actorId).set(JdEntity::getUpdateTime, now));
  }

  @Override
  public int bindDemand(String jdId, String demandId, String actorId, Instant now) {
    return baseMapper.update(Wrappers.<JdEntity>lambdaUpdate().eq(JdEntity::getId, jdId)
        .isNull(JdEntity::getDemandId).set(JdEntity::getDemandId, demandId)
        .set(JdEntity::getUpdateId, actorId).set(JdEntity::getUpdateTime, now));
  }

  @Override
  public JdEntity findSourceJd(String ownerUserId, String fingerprint) {
    return baseMapper.selectOne(Wrappers.lambdaQuery(JdEntity.class)
        .eq(JdEntity::getOwnerUserId, ownerUserId)
        .eq(JdEntity::getSourceFingerprint, fingerprint));
  }

  @Override
  public int countJdsForDemand(String demandId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(JdEntity.class).eq(JdEntity::getDemandId, demandId)));
  }

  @Override
  public String findJdIdByDemand(String demandId) {
    JdEntity jd =
        baseMapper.selectOne(
            Wrappers.lambdaQuery(JdEntity.class)
                .select(JdEntity::getId)
                .eq(JdEntity::getDemandId, demandId));
    return jd == null ? null : jd.getId();
  }

  @Override
  public int updateJdVersionNo(
      String id, String title, int versionNo, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(JdEntity.class)
            .eq(JdEntity::getId, id)
            .set(JdEntity::getTitle, title)
            .set(JdEntity::getCurrentVersionNo, versionNo)
            .set(JdEntity::getUpdateId, actorId)
            .set(JdEntity::getUpdateTime, now));
  }

  @Override
  public List<JdEntity> findPrivateOwnedJds(String ownerUserId, int limit) {
    return baseMapper.selectPage(new Page<>(1, Math.max(1, limit), false),
        Wrappers.lambdaQuery(JdEntity.class)
            .select(
                JdEntity::getId,
                JdEntity::getTitle,
                JdEntity::getOwnerUserId,
                JdEntity::getOwnerDepartmentId,
                JdEntity::isPublicVisible,
                JdEntity::isDeleted,
                JdEntity::getVersion)
            .eq(JdEntity::getOwnerUserId, ownerUserId)
            .eq(JdEntity::isDeleted, false)
            .orderByAsc(JdEntity::getCreateTime, JdEntity::getId)).getRecords();
  }

  @Override
  public int transferPrivateOwner(
      String id,
      String sourceUserId,
      String targetUserId,
      String targetDepartmentId,
      int version,
      String actorUserId,
      Instant now) {
    return baseMapper.update(
        Wrappers.<JdEntity>lambdaUpdate()
            .eq(JdEntity::getId, id)
            .eq(JdEntity::getOwnerUserId, sourceUserId)
            .eq(JdEntity::isDeleted, false)
            .eq(JdEntity::getVersion, version)
            .set(JdEntity::getOwnerUserId, targetUserId)
            .set(JdEntity::getOwnerDepartmentId, targetDepartmentId)
            .setIncrBy(JdEntity::getVersion, 1)
            .set(JdEntity::getUpdateId, actorUserId)
            .set(JdEntity::getUpdateTime, now));
  }
}
