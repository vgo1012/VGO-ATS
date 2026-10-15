package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.PersonalPreference;
import com.ezaigc.hr.intelligence.matching.mapper.PersonalPreferenceMapper;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** PersonalPreference 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class PersonalPreferencePersistenceServiceImpl
    extends ServiceImpl<PersonalPreferenceMapper, PersonalPreference>
    implements PersonalPreferencePersistenceService {

  /** 统计匹配方案引用列表。 */
  @Override
  public int countMatchPlanReferences(String planVersionId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(PersonalPreference.class)
                .eq(PersonalPreference::getPlanVersionId, planVersionId)));
  }

  /** 查询最新个人偏好。 */
  @Override
  public PersonalPreference findLatestPersonalPreference(String ownerUserId, String planVersionId) {
    var query =
        Wrappers.lambdaQuery(PersonalPreference.class)
            .eq(PersonalPreference::getOwnerUserId, ownerUserId)
            .eq(PersonalPreference::getPlanVersionId, planVersionId)
            .orderByDesc(PersonalPreference::getVersionNo);
    List<PersonalPreference> records =
        baseMapper.selectPage(new Page<>(1, 1, false), query).getRecords();
    return records.isEmpty() ? null : records.getFirst();
  }

  /** 查询个人偏好。 */
  @Override
  public PersonalPreference findPersonalPreference(String id) {
    return baseMapper.selectById(id);
  }
}
