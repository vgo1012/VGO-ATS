package com.ezaigc.hr.recruiting.submission.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClientSubmissionResponse {
  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 招聘需求 ID。 */
  private String demandId;
  /** 客户 ID。 */
  private String clientId;
  /** 项目 ID。 */
  private String projectId;
  /** 职位 ID。 */
  private String positionId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 客户推荐交付状态。 */
  private String deliveryStatus;
  /** 处理状态。 */
  private String processStatus;
  /** 发送时间。 */
  private Instant sentAt;
  /** 撤回时间。 */
  private Instant withdrawnAt;
  /** 撤回操作人用户 ID。 */
  private String withdrawnByUserId;
  /** 撤回原因。 */
  private String withdrawalReason;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  private String createId;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}
