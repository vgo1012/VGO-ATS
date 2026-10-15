package com.ezaigc.hr.integration.mail.application.provider;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

/** 单一邮箱的只读 UID 采集端口。 */
public interface MailIntakePort {

  Scan scan(Long afterUid, int limit);

  @Value
  @Accessors(fluent = true)
  @AllArgsConstructor
  class Scan {
    long uidValidity;
    long highestUid;
    List<MailMessage> messages;
    /** 本页已检查的 UID 上界；空页也只能推进到这里，不能越过未扫描的区间。 */
    long scannedThroughUid;

    public Scan(long uidValidity, long highestUid, List<MailMessage> messages) {
      this(uidValidity, highestUid, messages, highestUid);
    }
  }

  @Value
  @Accessors(fluent = true)
  @AllArgsConstructor
  class MailMessage {
    long uid;
    String messageId;
    String sender;
    String subject;
    Instant receivedAt;
    List<Attachment> resumeAttachments;
    String readFailureCode;

    public MailMessage(long uid, String messageId, String sender, String subject,
        Instant receivedAt, List<Attachment> resumeAttachments) {
      this(uid, messageId, sender, subject, receivedAt, resumeAttachments, null);
    }
  }

  @Value
  @Accessors(fluent = true)
  class Attachment {
    String fileName;
    byte[] content;
  }
}
