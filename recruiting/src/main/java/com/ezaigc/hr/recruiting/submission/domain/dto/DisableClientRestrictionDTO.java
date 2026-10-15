package com.ezaigc.hr.recruiting.submission.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DisableClientRestrictionDTO {
  /** 操作原因。 */
  private String reason;
}
