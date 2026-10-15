package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationIntentionResponse {
  /** 主键 ID。 */
  private String id;
  /** 候选人 ID。 */
  private String candidateId;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 期望薪资下限。 */
  private BigDecimal expectedSalaryMin;
  /** 期望薪资上限。 */
  private BigDecimal expectedSalaryMax;
  /** 薪资周期。 */
  private String salaryPeriod;
  /** 到岗时间或可用性。 */
  private String availability;
  /** 意向工作地点。 */
  private String intentionLocations;
  /** 操作原因。 */
  private String reason;
  /** 记录人用户 ID。 */
  private String recordedByUserId;
  /** 创建时间。 */
  private Instant createTime;
}
