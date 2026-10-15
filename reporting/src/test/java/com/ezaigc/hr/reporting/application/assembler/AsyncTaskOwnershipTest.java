package com.ezaigc.hr.reporting.application.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AsyncTaskOwnershipTest {
  @Test
  void keepsRequesterAndOwnerDistinctAndPreservesActualTimestamps() {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.requestedByUserId()).thenReturn("requester");
    when(task.ownerUserId()).thenReturn("owner");
    when(task.createTime()).thenReturn(Instant.parse("2026-08-01T01:00:00Z"));
    var response = new AsyncTaskAssembler().toResponse(task);
    assertThat(response.getRequestedByUserId()).isEqualTo("requester");
    assertThat(response.getOwnerUserId()).isEqualTo("owner");
    assertThat(response.getCreateTime()).isEqualTo(task.createTime());
  }
}
