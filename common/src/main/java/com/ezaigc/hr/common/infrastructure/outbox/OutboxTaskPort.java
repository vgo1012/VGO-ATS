package com.ezaigc.hr.common.infrastructure.outbox;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;

/** 同事务保存持久任务及其通知事件；调用方的业务事实必须处于同一数据库事务。 */
public interface OutboxTaskPort {
  TaskSnapshot enqueue(EnqueueTask work);
}
