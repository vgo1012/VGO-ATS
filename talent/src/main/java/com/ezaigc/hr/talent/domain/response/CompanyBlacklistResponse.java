package com.ezaigc.hr.talent.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompanyBlacklistResponse {
  /** 记录。 */
  private CompanyBlacklistRecordResponse record;
  /** 受影响流程数量。 */
  private int affectedWorkflowCount;
}
