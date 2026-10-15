package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.DepartmentAssignmentQueue;

/** DepartmentAssignmentQueue 实体的持久化服务。 */
public interface DepartmentAssignmentQueuePersistenceService
    extends IService<DepartmentAssignmentQueue> {

  int countAssignmentQueuesOwnedBy(String userId);

  /** 删除分配队列。 */
  int deleteAssignmentQueue(String departmentId);
}
