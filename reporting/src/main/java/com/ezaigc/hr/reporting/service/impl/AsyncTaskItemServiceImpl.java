package com.ezaigc.hr.reporting.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskItemEntity;
import com.ezaigc.hr.reporting.mapper.AsyncTaskItemMapper;
import com.ezaigc.hr.reporting.service.AsyncTaskItemService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 异步任务条目业务服务实现。 */
@Service
public class AsyncTaskItemServiceImpl
    extends ServiceImpl<AsyncTaskItemMapper, AsyncTaskItemEntity>
    implements AsyncTaskItemService {

  @Override
  public List<AsyncTaskItemEntity> findByTaskId(String taskId) {
    return list(
        Wrappers.<AsyncTaskItemEntity>lambdaQuery()
            .eq(AsyncTaskItemEntity::getTaskId, taskId)
            .orderByAsc(AsyncTaskItemEntity::getId));
  }

  @Override
  public boolean removeByTaskIdAndType(String taskId, String itemType) {
    return remove(
        Wrappers.<AsyncTaskItemEntity>lambdaQuery()
            .eq(AsyncTaskItemEntity::getTaskId, taskId)
            .eq(AsyncTaskItemEntity::getItemType, itemType));
  }
}
