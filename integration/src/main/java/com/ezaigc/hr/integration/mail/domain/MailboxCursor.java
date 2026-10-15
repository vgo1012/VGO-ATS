package com.ezaigc.hr.integration.mail.domain;

import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MailboxCursor extends StandardPersistenceFields {
  private String mailboxKey;
  private String folder;
  private long uidValidity;
  private long lastUid;
  private Instant initializedAt;
  private Instant lastSuccessAt;
  private long version;
}
