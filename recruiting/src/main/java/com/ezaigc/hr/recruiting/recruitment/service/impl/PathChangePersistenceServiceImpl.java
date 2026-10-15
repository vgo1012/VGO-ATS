package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.PathChange;
import com.ezaigc.hr.recruiting.recruitment.mapper.PathChangeMapper;
import com.ezaigc.hr.recruiting.recruitment.service.PathChangePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** PathChange 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class PathChangePersistenceServiceImpl extends ServiceImpl<PathChangeMapper, PathChange>
    implements PathChangePersistenceService {

  @Override
  public List<PathChange> findPathChanges(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(PathChange.class)
            .eq(PathChange::getApplicationId, applicationId)
            .orderByAsc(PathChange::getCreateTime, PathChange::getId));
  }
}
