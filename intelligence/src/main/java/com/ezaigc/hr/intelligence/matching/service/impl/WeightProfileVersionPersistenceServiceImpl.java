package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.mapper.WeightProfileVersionMapper;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** WeightProfileVersion 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class WeightProfileVersionPersistenceServiceImpl
    extends ServiceImpl<WeightProfileVersionMapper, WeightProfileVersion>
    implements WeightProfileVersionPersistenceService {

  /** 查询权重配置方案版本。 */
  @Override
  public WeightProfileVersion findWeightProfileVersion(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询权重配置方案版本操作人编号。 */
  @Override
  public WeightProfileVersion findWeightProfileVersionByNumber(String profileId, int versionNo) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(WeightProfileVersion.class)
            .eq(WeightProfileVersion::getProfileId, profileId)
            .eq(WeightProfileVersion::getVersionNo, versionNo));
  }

  @Override
  public List<WeightProfileVersion> findCurrentWeightProfileVersions(Set<String> profileIds) {
    return profileIds.isEmpty() ? List.of() : baseMapper.findCurrentVersions(profileIds);
  }

  /** 新增权重配置方案版本。 */
  @Override
  public int insertWeightProfileVersion(
      String id,
      String profileId,
      int versionNo,
      int skills,
      int role,
      int years,
      int responsibility,
      int industryProject,
      String actorId,
      Instant now) {
    WeightProfileVersion version = new WeightProfileVersion();
    version.setId(id);
    version.setProfileId(profileId);
    version.setVersionNo(versionNo);
    version.setSkillsWeight(skills);
    version.setRoleWeight(role);
    version.setYearsWeight(years);
    version.setResponsibilityWeight(responsibility);
    version.setIndustryProjectWeight(industryProject);
    version.setCreateId(actorId);
    version.setCreateTime(now);
    return baseMapper.insert(version);
  }

  @Override
  public int saveScreeningSettings(String id, String settingsJson) {
    return baseMapper.update(Wrappers.lambdaUpdate(WeightProfileVersion.class)
        .eq(WeightProfileVersion::getId, id)
        .isNull(WeightProfileVersion::getScreeningSettingsJson)
        .set(WeightProfileVersion::getScreeningSettingsJson, settingsJson));
  }

  /** 删除权重配置方案版本列表。 */
  @Override
  public int deleteWeightProfileVersions(String profileId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(WeightProfileVersion.class)
            .eq(WeightProfileVersion::getProfileId, profileId));
  }
}
