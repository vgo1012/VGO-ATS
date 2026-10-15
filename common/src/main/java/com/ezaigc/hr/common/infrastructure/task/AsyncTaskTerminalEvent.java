package com.ezaigc.hr.common.infrastructure.task;

import lombok.Value;

/** 持久化异步任务提交终态后的进程内提示；消费者仍以数据库状态为准。 */
@Value
public class AsyncTaskTerminalEvent {
  String taskType;
  String taskId;
  String resourceId;
  String status;
}
