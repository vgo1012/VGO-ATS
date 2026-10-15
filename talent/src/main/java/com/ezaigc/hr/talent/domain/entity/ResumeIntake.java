package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_resume_intake")
public class ResumeIntake extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 批次 ID。 */
  @TableField("batch_id")
  private String batchId;

  /** 来源渠道 ID。 */
  @TableField("source_channel_id")
  private String sourceChannelId;

  /** 来源招聘顾问用户 ID。 */
  @TableField("source_recruiter_user_id")
  private String sourceRecruiterUserId;

  @TableField(exist = false)
  private String defaultOwnerUserId;

  /** 批次岗位关联，仅查询投影。 */
  @TableField(exist = false)
  private String jdVersionId;

  /** 权限裁剪后的岗位名称，仅查询投影。 */
  @TableField(exist = false)
  private String jdTitle;
  /** 有权限查看的岗位业务编号。 */
  @TableField(exist = false)
  private String jdCode;

  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;

  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;

  /** 处理人用户 ID。 */
  @TableField("handled_by_user_id")
  private String handledByUserId;

  /** 上传时的原始文件名。 */
  @TableField("original_file_name")
  private String originalFileName;

  /** 文件媒体类型。 */
  @TableField("media_type")
  private String mediaType;

  /** 文件大小（字节）。 */
  @TableField("size_bytes")
  private long sizeBytes;

  /** 内容 SHA-256 摘要。 */
  @TableField("content_sha256")
  private String contentSha256;

  /** 对象存储键。 */
  @TableField("object_key")
  private String objectKey;

  /** 同内容文件共享的解析处理 ID。 */
  @TableField("processing_id")
  private String processingId;

  /** 共享解析状态，仅查询投影。 */
  @TableField(exist = false)
  private String processingStatus;

  /** 共享解析尝试次数，仅查询投影。 */
  @TableField(exist = false)
  private Integer processingAttempt;

  /** 解析出的姓名，仅用于有权查看收件的人工确认预填。 */
  @TableField(exist = false)
  private String parsedFullName;

  /** 解析出的邮箱，仅用于有权查看收件的人工确认预填。 */
  @TableField(exist = false)
  private String parsedEmail;

  /** 解析出的手机号，仅用于有权查看收件的人工确认预填。 */
  @TableField(exist = false)
  private String parsedPhone;

  /** 是否存在相同文件哈希的其他收件，包含没有共享处理 ID 的历史记录。 */
  @TableField(exist = false)
  private boolean duplicateContent;

  /** 安全扫描状态。 */
  @TableField("scan_status")
  private String scanStatus;

  /** 当前状态。 */
  @TableField("status")
  private String status;

  /** 失败编码。 */
  @TableField("failure_code")
  private String failureCode;

  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;

  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;

  /** 关联岗位的评分运行 ID。 */
  @TableField("score_run_id")
  private String scoreRunId;

  /** 关联岗位的评分状态。 */
  @TableField("score_status")
  private String scoreStatus;

  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version
  private long version;
}
