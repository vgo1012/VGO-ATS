package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogProbe;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.constant.ResumeIntakeWorkerConstants;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 收件独立状态机和领域 Outbox 不写通用任务表，由本探针补充持久积压。 */
@Component
@Order(20)
public class ResumeIntakePersistentBacklogProbe implements PersistentTaskBacklogProbe {
  private static final String OUTBOX_PUBLISH = "OUTBOX_PUBLISH";
  @Resource private ObjectProvider<DataSource> dataSourceProvider;
  @Resource private ObjectProvider<ResumeIntakeProcessingPersistenceService> processingProvider;
  @Resource private ObjectProvider<ResumeIntakeOutboxService> outboxProvider;

  @Override
  public boolean supports(String scheduleCode, List<String> businessTypes) {
    return dataSourceProvider.getIfAvailable() != null
        && (ResumeIntakeWorkerConstants.PARSE.equals(scheduleCode)
            || OUTBOX_PUBLISH.equals(scheduleCode));
  }

  @Override
  public PersistentTaskBacklogSnapshot snapshot(
      String scheduleCode, List<String> businessTypes, Instant now) {
    if (ResumeIntakeWorkerConstants.PARSE.equals(scheduleCode)) {
      return processingProvider.getObject().findReadyBacklog(now);
    }
    if (OUTBOX_PUBLISH.equals(scheduleCode)) {
      return outboxProvider.getObject().findReadyBacklog(now);
    }
    throw new IllegalArgumentException("unsupported resume intake backlog schedule");
  }
}
