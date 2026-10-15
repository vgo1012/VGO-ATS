package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import com.ezaigc.hr.recruiting.demand.mapper.JdSourceMapper;
import com.ezaigc.hr.recruiting.demand.service.JdSourcePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JdSourcePersistenceServiceImpl extends ServiceImpl<JdSourceMapper, JdSourceEntity>
    implements JdSourcePersistenceService {
  @Override
  public JdSourceEntity findSource(String actorId, String sourceType, String sourceKey) {
    return baseMapper.selectOne(Wrappers.<JdSourceEntity>lambdaQuery()
        .eq(JdSourceEntity::getOwnerUserId, actorId).eq(JdSourceEntity::getSourceType, sourceType)
        .eq(JdSourceEntity::getSourceKey, sourceKey));
  }

  @Override
  public List<JdSourceEntity> findPage(String jdVersionId, String actorId, boolean scopeAll, StableCursor cursor, int limit) {
    var query = Wrappers.<JdSourceEntity>lambdaQuery().eq(JdSourceEntity::getJdVersionId, jdVersionId)
        .and(!scopeAll, visible -> visible.eq(JdSourceEntity::getSourceType, "MANUAL")
            .or().eq(JdSourceEntity::getOwnerUserId, actorId))
        .and(cursor != null, page -> page.lt(JdSourceEntity::getCreateTime, cursor.getSortTime())
            .or(same -> same.eq(JdSourceEntity::getCreateTime, cursor.getSortTime()).lt(JdSourceEntity::getId, cursor.getId())))
        .orderByDesc(JdSourceEntity::getCreateTime, JdSourceEntity::getId);
    return baseMapper.selectPage(new Page<JdSourceEntity>(1, limit, false), query).getRecords();
  }
}
