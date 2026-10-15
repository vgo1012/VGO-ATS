package com.ezaigc.hr.common.infrastructure.task;

import lombok.Value;

/** 已核对持久绑定的进程内唤醒提示；任务状态始终以数据库为准。 */
@Value
public class AsyncTaskAvailable {
    String taskType;
    String taskId;
    String traceId;
}
