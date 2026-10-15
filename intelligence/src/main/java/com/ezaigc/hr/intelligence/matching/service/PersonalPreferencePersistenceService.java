package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.PersonalPreference;

/** PersonalPreference 实体的持久化服务。 */
public interface PersonalPreferencePersistenceService extends IService<PersonalPreference> {

  /** 统计匹配方案引用列表。 */
  int countMatchPlanReferences(String planVersionId);

  /** 查询最新个人偏好。 */
  PersonalPreference findLatestPersonalPreference(String ownerUserId, String planVersionId);

  /** 查询个人偏好。 */
  PersonalPreference findPersonalPreference(String id);
}
