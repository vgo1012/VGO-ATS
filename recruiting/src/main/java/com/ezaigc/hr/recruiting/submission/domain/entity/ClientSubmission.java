package com.ezaigc.hr.recruiting.submission.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("submission_client_submission")
public class ClientSubmission extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 招聘需求 ID。 */
  @TableField("demand_id")
  private String demandId;
  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;
  /** 项目 ID。 */
  @TableField("project_id")
  private String projectId;
  /** 职位 ID。 */
  @TableField("position_id")
  private String positionId;
  /** 职位描述（JD）ID。 */
  @TableField("jd_id")
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 客户推荐交付状态。 */
  @TableField("delivery_status")
  private String deliveryStatus;
  /** 处理状态。 */
  @TableField("process_status")
  private String processStatus;
  /** 发送时间。 */
  @TableField("sent_at")
  private Instant sentAt;
  /** 撤回时间。 */
  @TableField("withdrawn_at")
  private Instant withdrawnAt;
  /** 撤回操作人用户 ID。 */
  @TableField("withdrawn_by_user_id")
  private String withdrawnByUserId;
  /** 撤回原因。 */
  @TableField("withdrawal_reason")
  private String withdrawalReason;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;

  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

}
