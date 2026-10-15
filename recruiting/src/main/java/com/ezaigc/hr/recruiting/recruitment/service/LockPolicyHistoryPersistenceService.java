package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicyHistory;
import java.util.List;

/** LockPolicyHistory 实体的持久化服务。 */
public interface LockPolicyHistoryPersistenceService extends IService<LockPolicyHistory> {

  List<LockPolicyHistory> policyHistory(int limit);
}
