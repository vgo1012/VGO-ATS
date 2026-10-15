package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationFollowUpResponse {
  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 招聘流程步骤 ID。 */
  private String workflowStepId;
  /** 通知或沟通渠道。 */
  private String channel;
  /** 摘要。 */
  private String summary;
  /** 下一步方案。 */
  private String nextPlan;
  /** 联系时间。 */
  private Instant contactedAt;
  /** 记录人用户 ID。 */
  private String recordedByUserId;
  /** 创建时间。 */
  private Instant createTime;
}
