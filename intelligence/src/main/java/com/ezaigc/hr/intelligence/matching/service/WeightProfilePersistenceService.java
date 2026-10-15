package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** WeightProfile 实体的持久化服务。 */
public interface WeightProfilePersistenceService extends IService<WeightProfile> {

  /** 查询权重配置方案列表。 */
  List<WeightProfile> findWeightProfiles(
      String ownerUserId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  long countWeightProfiles(String ownerUserId, boolean scopeAll, Set<String> managedUserIds);

  /** 查询权重配置方案。 */
  WeightProfile findWeightProfile(String id);

  /** 新增权重配置方案。 */
  int insertWeightProfile(String id, String ownerUserId, String name, Instant now);

  /** 更新权重配置方案。 */
  int updateWeightProfile(
      String id, String ownerUserId, String name, int versionNo, long version, Instant now);

  /** 删除权重配置方案。 */
  int deleteWeightProfile(String id, String ownerUserId, long version);
}
