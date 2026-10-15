package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicy;

/** LockPolicy 实体的持久化服务。 */
public interface LockPolicyPersistenceService extends IService<LockPolicy> {

  LockPolicy policy();
}
