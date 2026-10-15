package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.ScopeGrant;
import java.util.List;

/** ScopeGrant 实体的持久化服务。 */
public interface ScopeGrantPersistenceService extends IService<ScopeGrant> {

  List<String> findGrantedDepartmentIds(String userId);

  /** 统计公司范围。 */
  int countCompanyScope(String userId);
}
