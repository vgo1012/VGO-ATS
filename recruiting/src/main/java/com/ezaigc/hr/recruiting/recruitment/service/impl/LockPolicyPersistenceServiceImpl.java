package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicy;
import com.ezaigc.hr.recruiting.recruitment.mapper.LockPolicyMapper;
import com.ezaigc.hr.recruiting.recruitment.service.LockPolicyPersistenceService;
import org.springframework.stereotype.Service;

/** LockPolicy 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class LockPolicyPersistenceServiceImpl extends ServiceImpl<LockPolicyMapper, LockPolicy>
    implements LockPolicyPersistenceService {

  @Override
  public LockPolicy policy() {
    return baseMapper.selectById("DEFAULT");
  }
}
