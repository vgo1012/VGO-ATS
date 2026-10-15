package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicyHistory;
import com.ezaigc.hr.recruiting.recruitment.mapper.LockPolicyHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.service.LockPolicyHistoryPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** LockPolicyHistory 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class LockPolicyHistoryPersistenceServiceImpl
    extends ServiceImpl<LockPolicyHistoryMapper, LockPolicyHistory>
    implements LockPolicyHistoryPersistenceService {

  @Override
  public List<LockPolicyHistory> policyHistory(int limit) {
    return baseMapper
        .selectPage(
            new Page<>(1, limit, false),
            Wrappers.lambdaQuery(LockPolicyHistory.class)
                .eq(LockPolicyHistory::getPolicyKey, "DEFAULT")
                .orderByDesc(LockPolicyHistory::getOccurredAt, LockPolicyHistory::getId))
        .getRecords();
  }
}
