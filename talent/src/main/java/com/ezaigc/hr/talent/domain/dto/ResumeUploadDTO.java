package com.ezaigc.hr.talent.domain.dto;

import java.io.IOException;
import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.InputStreamSource;

@Getter
@AllArgsConstructor
public class ResumeUploadDTO {
  /** 上传时的原始文件名。 */
  private String originalFileName;
  /** 文件大小（字节）。 */
  private long sizeBytes;
  /** 内容来源。 */
  private InputStreamSource contentSource;

  public InputStream openStream() throws IOException {
    if (contentSource == null) throw new IOException("resume content source is unavailable");
    InputStream input = contentSource.getInputStream();
    if (input == null) throw new IOException("resume content stream is unavailable");
    return input;
  }
}
