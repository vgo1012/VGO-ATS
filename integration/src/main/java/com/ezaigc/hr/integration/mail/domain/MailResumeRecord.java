package com.ezaigc.hr.integration.mail.domain;

import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MailResumeRecord extends StandardPersistenceFields {
  private String id;
  private String mailboxKey;
  private String folder;
  private String sourceChannelId;
  private long uidValidity;
  private long mailUid;
  private String messageIdHash;
  private String senderEmail;
  private String subject;
  private Instant receivedAt;
  private String attachmentName;
  private String mediaType;
  private long sizeBytes;
  private String contentSha256;
  private String objectKey;
  private String parsedJdCode;
  private String parsedJdTitle;
  private String parsedRegion;
  private String matchType;
  private String status;
  private String failureCode;
  private String failureDetail;
  private String jdId;
  private String jdVersionId;
  private String ownerUserId;
  private String ownerDepartmentId;
  private String intakeId;
  private String linkedByUserId;
  private Instant linkedAt;
  private String linkReason;
  private long version;
}
