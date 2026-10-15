package com.ezaigc.hr.talent.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumePreviewResponse {
  /** 主键 ID。 */
  private String id;
  /** 文件名。 */
  private String fileName;
  /** 文件媒体类型。 */
  private String mediaType;
  /** 文件大小（字节）。 */
  private long sizeBytes;
  /** 文件内容 SHA-256 摘要。 */
  private String sha256;
  /** 当前状态。 */
  private String status;
  /** 是否允许下载。 */
  private boolean downloadable;
}
