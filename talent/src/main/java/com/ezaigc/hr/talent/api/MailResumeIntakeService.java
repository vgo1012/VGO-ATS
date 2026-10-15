package com.ezaigc.hr.talent.api;

import lombok.Value;
import lombok.experimental.Accessors;

/** 邮件模块复用标准简历收件链路的边界。 */
public interface MailResumeIntakeService {

  Result importResume(Command command);

  @Value
  @Accessors(fluent = true)
  class Command {
    String messageId;
    String sourceChannelId;
    String jdVersionId;
    String ownerUserId;
    String ownerDepartmentId;
    String fileName;
    long sizeBytes;
    String objectKey;
  }

  @Value
  @Accessors(fluent = true)
  class Result {
    String intakeId;
    String status;
  }
}
