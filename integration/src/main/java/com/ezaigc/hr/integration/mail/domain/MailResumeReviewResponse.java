package com.ezaigc.hr.integration.mail.domain;

import java.time.Instant;
import lombok.Value;

@Value
public class MailResumeReviewResponse {
  String id;
  Instant receivedAt;
  String sender;
  String subject;
  String attachmentName;
  String parsedJdCode;
  String parsedJdTitle;
  String parsedRegion;
  String failureCode;
  String failureDetail;
  String status;
  String matchType;
  String jdId;
  String jdVersionId;
  String intakeId;
  String linkedByUserId;
  Instant linkedAt;
  String version;
}
