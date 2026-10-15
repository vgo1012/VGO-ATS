package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import java.time.Instant;

/**
 * 身份管理持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary for identity administration use cases.
 */
public interface IdentityAdministrationPersistenceService
    extends IService<DepartmentRecord>, IdentityAdministrationSqlOperations {
  /** 统计部门编码。 */
  int countDepartmentCode(String code);

  /** 新增部门。 */
  int insertDepartment(
      String id, String code, String name, String parentId, String actorId, Instant now);

  /** 更新部门。 */
  int updateDepartment(
      String id,
      String name,
      String parentId,
      String status,
      long version,
      String actorId,
      Instant now);

  int countActiveChildDepartments(String departmentId);

  /** 按版本删除没有引用的部门。 */
  int deleteDepartment(String departmentId, long version);
}
