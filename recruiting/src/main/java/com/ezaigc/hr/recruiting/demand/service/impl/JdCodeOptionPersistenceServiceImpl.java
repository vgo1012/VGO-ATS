package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import com.ezaigc.hr.recruiting.demand.mapper.JdCodeOptionMapper;
import com.ezaigc.hr.recruiting.demand.service.JdCodeOptionPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** JD 编号维度选项的薄持久化实现。 */
@Service
public class JdCodeOptionPersistenceServiceImpl extends ServiceImpl<JdCodeOptionMapper, JdCodeOptionEntity>
    implements JdCodeOptionPersistenceService {
  @Override
  public List<JdCodeOptionEntity> findCodeOptions() {
    return baseMapper.selectList(Wrappers.<JdCodeOptionEntity>lambdaQuery()
        .orderByAsc(JdCodeOptionEntity::getKind, JdCodeOptionEntity::getCode));
  }

  @Override
  public JdCodeOptionEntity findCodeOption(String kind, String code) {
    return baseMapper.selectOne(Wrappers.<JdCodeOptionEntity>lambdaQuery()
        .eq(JdCodeOptionEntity::getKind, kind).eq(JdCodeOptionEntity::getCode, code));
  }

  @Override
  public JdCodeOptionEntity findCodeOptionByName(String kind, String name) {
    return baseMapper.findCodeOptionByName(kind, name);
  }

  @Override
  public String lastOptionCode(String kind) {
    return baseMapper.lastOptionCode(kind);
  }

  @Override
  public int insertCodeOption(JdCodeOptionEntity option) {
    return baseMapper.insert(option);
  }

}
