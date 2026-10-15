package com.ezaigc.hr.reporting.application.support;

import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogProbe;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 通用异步任务表的可领取积压；按目录声明的业务类型一次汇总。 */
@Component
@Order(10)
public class AsyncTaskPersistentBacklogProbe implements PersistentTaskBacklogProbe {
  @Resource private ObjectProvider<DataSource> dataSourceProvider;
  @Resource private ObjectProvider<AsyncTaskService> taskProvider;

  @Override
  public boolean supports(String scheduleCode, List<String> businessTypes) {
    return dataSourceProvider.getIfAvailable() != null
        && businessTypes != null && !businessTypes.isEmpty();
  }

  @Override
  public PersistentTaskBacklogSnapshot snapshot(
      String scheduleCode, List<String> businessTypes, Instant now) {
    return taskProvider.getObject().findReadyBacklog(businessTypes, now);
  }
}
