package com.ezaigc.hr.talent.domain.dto;
import java.time.Instant;
import lombok.Data;
@Data
public class ResumeLifecycleReadyDTO {
  private String candidateId;
  private String resumeVersionId;
  private Instant enteredAt;
}
