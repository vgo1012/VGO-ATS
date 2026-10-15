package com.ezaigc.hr.reporting.outbox.application.assembler;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.application.assembler.AsyncTaskAssembler;
import com.ezaigc.hr.reporting.outbox.domain.entity.SearchTaskRecoveryEntity;
import com.ezaigc.hr.reporting.outbox.domain.response.SearchTaskRecoveryResponse;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class SearchTaskRecoveryAssembler {
  @Resource private AsyncTaskAssembler tasks;

  public SearchTaskRecoveryResponse toResponse(SearchTaskRecoveryEntity recovery, TaskSnapshot task) {
    return new SearchTaskRecoveryResponse(recovery.getOriginalTaskId(), tasks.toResponse(task),
        recovery.getOutboxEventId(), recovery.getReason(), recovery.getRequestedByUserId(), recovery.getCreateTime());
  }
}
