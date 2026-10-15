package com.ezaigc.hr.recruiting.interview.domain.entity;

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
@TableName("interview_interview")
public class Interview extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 职位描述（JD）ID。 */
  @TableField("jd_id")
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 招聘流程模板版本 ID。 */
  @TableField("workflow_template_version_id")
  private String workflowTemplateVersionId;
  /** 招聘流程步骤 ID。 */
  @TableField("workflow_step_id")
  private String workflowStepId;
  /** 步骤编码。 */
  @TableField("step_code")
  private String stepCode;
  /** 面试类型。 */
  @TableField("interview_type")
  private String interviewType;
  /** 轮次序号。 */
  @TableField("round_no")
  private int roundNo;
  /** 客户推荐记录 ID。 */
  @TableField("client_submission_id")
  private String clientSubmissionId;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 计划开始时间。 */
  @TableField("scheduled_start_at")
  private Instant scheduledStartAt;
  /** 计划结束时间。 */
  @TableField("scheduled_end_at")
  private Instant scheduledEndAt;
  @TableField("timezone_id")
  private String timezoneId;
  /** 面试会议方式。 */
  @TableField("meeting_mode")
  private String meetingMode;
  /** 地点文本。 */
  @TableField("location_text")
  private String locationText;
  /** 线上会议地址。 */
  @TableField("meeting_url")
  private String meetingUrl;
  /** 候选人通知渠道。 */
  @TableField("candidate_notification_channel")
  private String candidateNotificationChannel;
  @TableField("candidate_confirmation_status")
  private String candidateConfirmationStatus;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  @TableField("next_responsible_user_id")
  private String nextResponsibleUserId;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

  /** 排期面评文字，不代表正式结果。 */
  @TableField("initial_comment")
  private String initialComment;

}
