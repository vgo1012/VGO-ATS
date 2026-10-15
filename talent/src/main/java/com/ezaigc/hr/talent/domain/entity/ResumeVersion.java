package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_resume_version")
public class ResumeVersion extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 简历 ID。 */
  @TableField("resume_id")
  private String resumeId;
  @TableField("intake_id")
  private String intakeId;
  /** 业务版本号。 */
  @TableField("version_no")
  private int versionNo;
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
  /** 当前状态。 */
  @TableField("status")
  private String status;

}
