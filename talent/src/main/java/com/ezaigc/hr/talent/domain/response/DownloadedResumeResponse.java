package com.ezaigc.hr.talent.domain.response;

import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DownloadedResumeResponse {
  /** 文件名。 */
  private String fileName;
  /** 文件媒体类型。 */
  private String mediaType;
  /** 文件大小（字节）。 */
  private long sizeBytes;
  /** 内容。 */
  private InputStream content;
}
