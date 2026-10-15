package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OnboardingEventResponse {
  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 入职时间。 */
  private Instant onboardedAt;
  /** 来源。 */
  private String source;
  /** 外部来源引用。 */
  private String sourceReference;
  /** 操作原因。 */
  private String reason;
  /** 操作人用户 ID。 */
  private String operatedByUserId;
  /** 创建时间。 */
  private Instant createTime;
}
