package com.ezaigc.hr.talent.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResourceEntity {
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 上传时的原始文件名。 */
  private String originalFileName;
  /** 文件媒体类型。 */
  private String mediaType;
  /** 内容 SHA-256 摘要。 */
  private String contentSha256;
  /** 对象存储键。 */
  private String objectKey;
  /** 候选人 ID。 */
  private String candidateId;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
}
