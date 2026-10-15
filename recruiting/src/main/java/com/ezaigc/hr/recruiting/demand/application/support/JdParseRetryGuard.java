package com.ezaigc.hr.recruiting.demand.application.support;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_TASK_TYPE;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_RESOURCE_TYPE;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskRetryGuard;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** 任务中心及内部重试入口均重新执行 JD 解析准入。 */
@Component
public class JdParseRetryGuard implements AsyncTaskRetryGuard {
  @Resource private JdParsingAdmissionPolicy admission;

  @Override
  public void requireRetryAllowed(TaskSnapshot task) {
    if (!JD_PARSE_TASK_TYPE.equals(task.taskType())) return;
    if (!JD_PARSE_RESOURCE_TYPE.equals(task.resourceType())) {
      throw PlatformExceptions.unprocessable("TASK_RESOURCE_TYPE_INVALID", "JD 解析任务资源类型不正确");
    }
    admission.requireUserAccess(task.resourceId());
  }
}
