package com.ezaigc.hr.recruiting.demand.domain.dto;
import lombok.Value;
import lombok.ToString;
@Value
public class CreateJdResumeSourceDTO {
  @ToString.Exclude String candidateId;
  @ToString.Exclude String resumeVersionId;
  @ToString.Exclude String sourceMessageId;
  @ToString.Exclude String senderEmail;
  @ToString.Exclude String subject;
  @ToString.Exclude String plainBody;
}
