package com.ezaigc.hr.integration.mail.domain;

import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MailResumeIngestRun extends StandardPersistenceFields {
  private String id;
  private String slotKey;
  private String mailboxKey;
  private String status;
  private int scannedCount;
  private int attachmentCount;
  private int autoMatchedCount;
  private int reviewRequiredCount;
  private int duplicateCount;
  private int failedCount;
  private String recentErrorCode;
  private String recentErrorDetail;
  private Instant startedAt;
  private Instant finishedAt;
  private long version;
}
