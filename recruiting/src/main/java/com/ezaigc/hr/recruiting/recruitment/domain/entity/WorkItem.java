package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkItem {
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  private String stepId;
  /** 步骤编码。 */
  private String stepCode;
  /** 步骤状态。 */
  private String stepStatus;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 截止时间。 */
  private Instant dueAt;
  /** 逾期。 */
  private boolean overdue;
  /** 当前招聘阶段。 */
  private String currentStage;
  /** 流程结果。 */
  private String outcome;
  /** 应聘流程创建时间。 */
  private Instant applicationCreatedAt;
  /** 已经耗时的秒数。 */
  private long elapsedSeconds;
}
