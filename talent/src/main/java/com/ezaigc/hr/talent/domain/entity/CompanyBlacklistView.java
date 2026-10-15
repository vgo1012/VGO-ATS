package com.ezaigc.hr.talent.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyBlacklistView {
  /** 记录。 */
  private CompanyBlacklistRecord record;
  /** 受影响流程数量。 */
  private int affectedWorkflowCount;
}
