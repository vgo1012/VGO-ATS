package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.ScopeGrant;
import com.ezaigc.hr.identity.mapper.ScopeGrantMapper;
import com.ezaigc.hr.identity.service.ScopeGrantPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** ScopeGrant 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ScopeGrantPersistenceServiceImpl extends ServiceImpl<ScopeGrantMapper, ScopeGrant>
    implements ScopeGrantPersistenceService {

  @Override
  public List<String> findGrantedDepartmentIds(String userId) {
    return baseMapper.selectObjs(
        Wrappers.lambdaQuery(ScopeGrant.class)
            .select(ScopeGrant::getScopeValue)
            .eq(ScopeGrant::getUserId, userId)
            .eq(ScopeGrant::getScopeType, "DEPARTMENT")
            .orderByAsc(ScopeGrant::getScopeValue));
  }

  /** 统计公司范围。 */
  @Override
  public int countCompanyScope(String userId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(ScopeGrant.class)
                .eq(ScopeGrant::getUserId, userId)
                .eq(ScopeGrant::getScopeType, "COMPANY")
                .eq(ScopeGrant::getScopeValue, "*")));
  }
}
