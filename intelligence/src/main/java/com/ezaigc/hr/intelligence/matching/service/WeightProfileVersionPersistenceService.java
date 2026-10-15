package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** WeightProfileVersion 实体的持久化服务。 */
public interface WeightProfileVersionPersistenceService extends IService<WeightProfileVersion> {

  /** 查询权重配置方案版本。 */
  WeightProfileVersion findWeightProfileVersion(String id);

  /** 查询权重配置方案版本操作人编号。 */
  WeightProfileVersion findWeightProfileVersionByNumber(String profileId, int versionNo);

  /** 批量查询权重方案当前版本，供管理列表避免逐行查询。 */
  List<WeightProfileVersion> findCurrentWeightProfileVersions(Set<String> profileIds);

  /** 新增权重配置方案版本。 */
  int insertWeightProfileVersion(
      String id,
      String profileId,
      int versionNo,
      int skills,
      int role,
      int years,
      int responsibility,
      int industryProject,
      String actorId,
      Instant now);

  /** 在创建版本的同一事务内保存个人筛选快照。 */
  int saveScreeningSettings(String id, String settingsJson);

  /** 删除权重配置方案版本列表。 */
  int deleteWeightProfileVersions(String profileId);
}
