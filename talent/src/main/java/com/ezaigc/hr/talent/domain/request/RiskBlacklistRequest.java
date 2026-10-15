package com.ezaigc.hr.talent.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskBlacklistRequest {
  private @NotBlank @Size(max = 60) String reasonCode;
  private @NotBlank @Size(max = 1000) String reasonDetail;
  private @NotBlank @Size(max = 40) String sourceChannel;
  private @NotBlank @Size(max = 1000) String evidenceReference;
  /** 事件发生时间。 */
  private Instant occurredAt;

}
