package com.ezaigc.hr.recruiting.demand.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
/** 来源记录仅用于追溯，不构成正式招聘申请或评价。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_jd_resume_source")
public class JdResumeSourceEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 邮件来源记录 ID。 */
  @TableField("mail_source_id")
  @ToString.Exclude
  private String mailSourceId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  @ToString.Exclude
  private String candidateId;
  /** 录入时核对的候选人名称。 */
  @TableField("candidate_name")
  @ToString.Exclude
  private String candidateName;
  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  @ToString.Exclude
  private String resumeVersionId;
  /** 原始附件名称。 */
  @TableField("original_file_name")
  @ToString.Exclude
  private String originalFileName;
  /** 已入库附件内容哈希。 */
  @TableField("content_sha256")
  @ToString.Exclude
  private String contentSha256;
}
