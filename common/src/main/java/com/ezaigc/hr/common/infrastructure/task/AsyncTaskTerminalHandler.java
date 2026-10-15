package com.ezaigc.hr.common.infrastructure.task;

import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import java.time.Instant;

/** 处理器不再完成业务终态时，业务模块在共享任务终结事务内同步自身状态的窄接口。 */
public interface AsyncTaskTerminalHandler {
  /**
   * 接收租约回收、排队取消或处理器异常退出的 FAILED/CANCELLED；未知任务类型应忽略。
   * 错误编码来自本次共享任务终态，取消时为空，不得将所有失败误记为租约过期。
   * 同步执行且必须加入调用方事务，禁止远程 IO；异常必须向上传播以回滚任务、尝试及业务记录。
   */
  void terminalWithoutWorker(String taskType, String taskId, String resourceId,
      AsyncTaskStatus status, String errorCode, Instant finishedAt, String actorId);
}
