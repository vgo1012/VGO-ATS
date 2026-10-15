package com.ezaigc.hr.common.infrastructure.task;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;

/** 业务模块在失败任务重新排队前校验当前资源与操作人资格。 */
public interface AsyncTaskRetryGuard {
  /** 未知任务类型应忽略；加入调用方短事务，禁止外部 IO，拒绝异常必须向上传播。 */
  void requireRetryAllowed(TaskSnapshot task);
}
