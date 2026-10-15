package com.ezaigc.hr.reporting.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import org.springframework.stereotype.Component;

/**
 * 异步任务边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。
 */
@Component
public class AsyncTaskAssembler {

    public AsyncTaskResponse toResponse(TaskSnapshot value) {
        return new AsyncTaskResponse(
                value.id(),
                value.resourceId(),
                value.resourceType(),
                value.taskType(),
                value.status(),
                value.attemptCount(),
                value.maxAttempts(),
                value.progressPercent(),
                value.errorCode(),
                value.safeErrorDetail(),
                value.retryable(),
                value.resultResourceId(),
                format(value.version()),
                value.createTime(),
                value.startedAt(),
                value.finishedAt(),
                value.updateTime(),
                value.requestedByUserId(),
                value.ownerUserId());
    }
}
