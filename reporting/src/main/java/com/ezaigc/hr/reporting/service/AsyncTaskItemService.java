package com.ezaigc.hr.reporting.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskItemEntity;
import java.util.List;

public interface AsyncTaskItemService extends IService<AsyncTaskItemEntity> {
  List<AsyncTaskItemEntity> findByTaskId(String taskId);

  boolean removeByTaskIdAndType(String taskId, String itemType);
}
