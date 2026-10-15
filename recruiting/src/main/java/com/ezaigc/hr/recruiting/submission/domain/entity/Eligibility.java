package com.ezaigc.hr.recruiting.submission.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Eligibility {
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 客户 ID。 */
  private String clientId;
  /** 公司黑名单。 */
  private boolean companyBlacklist;
  /** 客户限制。 */
  private boolean clientRestriction;
  /** 是否符合条件。 */
  private boolean eligible;
  private List<String> blockingCodes;
}
