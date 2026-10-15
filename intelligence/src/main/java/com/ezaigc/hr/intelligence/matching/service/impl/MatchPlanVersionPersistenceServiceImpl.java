package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.enums.MatchingConfigurationStatus;
import com.ezaigc.hr.intelligence.matching.mapper.MatchPlanVersionMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** MatchPlanVersion 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MatchPlanVersionPersistenceServiceImpl
    extends ServiceImpl<MatchPlanVersionMapper, MatchPlanVersion>
    implements MatchPlanVersionPersistenceService {

  /** 查询匹配方案版本。 */
  @Override
  public MatchPlanVersion findMatchPlanVersion(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询匹配方案版本列表。 */
  @Override
  public List<MatchPlanVersion> findMatchPlanVersions(
      String jdVersionId, Instant cursorTime, String cursorId, int limit) {
    var query =
        Wrappers.lambdaQuery(MatchPlanVersion.class)
            .eq(jdVersionId != null, MatchPlanVersion::getJdVersionId, jdVersionId)
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(MatchPlanVersion::getCreateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(MatchPlanVersion::getCreateTime, cursorTime)
                                    .lt(MatchPlanVersion::getId, cursorId)))
            .orderByDesc(MatchPlanVersion::getCreateTime, MatchPlanVersion::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  /** 查询生效匹配方案版本。 */
  @Override
  public MatchPlanVersion findActiveMatchPlanVersion(String jdVersionId) {
    var baseline = baseMapper.selectById(DefaultScoringConstants.PLAN_VERSION_ID);
    if (baseline == null) return null;
    return baseMapper.selectPage(new Page<>(1, 1, false), Wrappers.lambdaQuery(MatchPlanVersion.class)
        .eq(MatchPlanVersion::getPlanId, baseline.getPlanId())
        .eq(MatchPlanVersion::getStatus, MatchingConfigurationStatus.ACTIVE.getCode())
        .orderByDesc(MatchPlanVersion::getVersionNo)).getRecords().stream().findFirst().orElse(null);
  }

  /** 新增匹配方案版本。 */
  @Override
  public int insertMatchPlanVersion(
      String id,
      String planId,
      String jdVersionId,
      int versionNo,
      String criterionPlanId,
      boolean skillsEnabled,
      boolean roleEnabled,
      boolean yearsEnabled,
      boolean responsibilityEnabled,
      boolean industryProjectEnabled,
      String actorId,
      Instant now) {
    MatchPlanVersion version = new MatchPlanVersion();
    version.setId(id);
    version.setPlanId(planId);
    version.setJdVersionId(jdVersionId);
    version.setVersionNo(versionNo);
    version.setStatus(MatchingConfigurationStatus.DRAFT.getCode());
    version.setControlledCriterionPlanVersionId(criterionPlanId);
    version.setSkillsEnabled(skillsEnabled);
    version.setRoleEnabled(roleEnabled);
    version.setYearsEnabled(yearsEnabled);
    version.setResponsibilityEnabled(responsibilityEnabled);
    version.setIndustryProjectEnabled(industryProjectEnabled);
    version.setVersion(0L);
    version.setCreateId(actorId);
    version.setConfiguredByUserId(actorId);
    version.setCreateTime(now);
    version.setUpdateId(actorId);
    version.setUpdateTime(now);
    return baseMapper.insert(version);
  }

  /** 更新匹配方案版本。 */
  @Override
  public int updateMatchPlanVersion(
      String id,
      boolean skillsEnabled,
      boolean roleEnabled,
      boolean yearsEnabled,
      boolean responsibilityEnabled,
      boolean industryProjectEnabled,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchPlanVersion.class)
            .eq(MatchPlanVersion::getId, id)
            .eq(MatchPlanVersion::getVersion, version)
            .setIncrBy(MatchPlanVersion::getVersion, 1)
            .eq(MatchPlanVersion::getStatus, MatchingConfigurationStatus.DRAFT.getCode())
            .set(MatchPlanVersion::isSkillsEnabled, skillsEnabled)
            .set(MatchPlanVersion::isRoleEnabled, roleEnabled)
            .set(MatchPlanVersion::isYearsEnabled, yearsEnabled)
            .set(MatchPlanVersion::isResponsibilityEnabled, responsibilityEnabled)
            .set(MatchPlanVersion::isIndustryProjectEnabled, industryProjectEnabled)
            .set(MatchPlanVersion::getConfiguredByUserId, actorId)
            .set(MatchPlanVersion::getUpdateId, actorId)
            .set(MatchPlanVersion::getUpdateTime, now));
  }

  /** 激活匹配方案。 */
  @Override
  public int activateMatchPlan(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchPlanVersion.class)
            .eq(MatchPlanVersion::getId, id)
            .eq(MatchPlanVersion::getVersion, version)
            .setIncrBy(MatchPlanVersion::getVersion, 1)
            .eq(MatchPlanVersion::getStatus, MatchingConfigurationStatus.DRAFT.getCode())
            .set(MatchPlanVersion::getStatus, MatchingConfigurationStatus.ACTIVE.getCode())
            .set(MatchPlanVersion::getJdVersionId, null)
            .set(MatchPlanVersion::getActiveSlot, 1)
            .set(MatchPlanVersion::getActivatedByUserId, actorId)
            .set(MatchPlanVersion::getActivatedAt, now)
            .set(MatchPlanVersion::getUpdateId, actorId)
            .set(MatchPlanVersion::getUpdateTime, now));
  }

  /** 删除匹配方案版本。 */
  @Override
  public int deleteMatchPlanVersion(String id, long version) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(MatchPlanVersion.class)
            .eq(MatchPlanVersion::getId, id)
            .eq(MatchPlanVersion::getVersion, version)
            .eq(MatchPlanVersion::getStatus, MatchingConfigurationStatus.DRAFT.getCode()));
  }

}
