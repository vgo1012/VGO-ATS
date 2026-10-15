package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.UserSupportDepartment;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** UserSupportDepartment 实体的持久化服务。 */
public interface UserSupportDepartmentPersistenceService extends IService<UserSupportDepartment> {

  /** 批量新增账号支援部门关系。 */
  boolean insertSupportDepartments(
      String userId, Set<String> departmentIds, String actorId, Instant now);

  List<String> findSupportDepartmentIds(String userId);

  int deleteSupportDepartments(String userId);
}
