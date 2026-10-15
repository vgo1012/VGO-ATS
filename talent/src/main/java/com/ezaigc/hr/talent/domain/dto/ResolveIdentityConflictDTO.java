package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResolveIdentityConflictDTO {
  /** 解决结果。 */
  private String resolution;
  private String chosenCandidateId;
  /** 操作原因。 */
  private String reason;
}
