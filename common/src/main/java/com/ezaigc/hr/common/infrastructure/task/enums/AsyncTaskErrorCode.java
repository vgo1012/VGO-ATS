package com.ezaigc.hr.common.infrastructure.task.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 共享任务端口对调用方公开的状态冲突编码。 */
@Getter
@RequiredArgsConstructor
public enum AsyncTaskErrorCode implements CodeDescEnum {
  TASK_STATE_CONFLICT("任务状态已变化");

  private final String desc;
}
