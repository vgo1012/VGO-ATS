package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.DepartmentAssignmentQueue;
import com.ezaigc.hr.identity.mapper.DepartmentAssignmentQueueMapper;
import com.ezaigc.hr.identity.service.DepartmentAssignmentQueuePersistenceService;
import org.springframework.stereotype.Service;

/** DepartmentAssignmentQueue 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DepartmentAssignmentQueuePersistenceServiceImpl
    extends ServiceImpl<DepartmentAssignmentQueueMapper, DepartmentAssignmentQueue>
    implements DepartmentAssignmentQueuePersistenceService {

  @Override
  public int countAssignmentQueuesOwnedBy(String userId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(DepartmentAssignmentQueue.class)
                .eq(DepartmentAssignmentQueue::getOwnerUserId, userId)));
  }

  /** 删除分配队列。 */
  @Override
  public int deleteAssignmentQueue(String departmentId) {
    return baseMapper.deleteById(departmentId);
  }
}
