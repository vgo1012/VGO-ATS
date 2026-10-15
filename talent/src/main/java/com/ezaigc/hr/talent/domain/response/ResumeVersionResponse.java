package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeVersionResponse {
  /** 主键 ID。 */
  private String id;
  /** 简历 ID。 */
  private String resumeId;
  private String intakeId;
  /** 业务版本号。 */
  private int versionNo;
  /** 上传时的原始文件名。 */
  private String originalFileName;
  /** 文件媒体类型。 */
  private String mediaType;
  /** 文件大小（字节）。 */
  private long sizeBytes;
  /** 内容 SHA-256 摘要。 */
  private String contentSha256;
  /** 当前状态。 */
  private String status;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
}
