package com.ezaigc.hr.talent.api;

/** 简历上传在 HTTP、应用和外部适配器之间共享的公开业务边界。 */
public final class ResumeUploadPolicy {

  /** 单个简历文件最大字节数（10 MiB）。 */
  public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

  /** 原始简历文件名最大字符数，与持久化字段宽度保持一致。 */
  public static final int MAX_FILE_NAME_LENGTH = 255;

  private ResumeUploadPolicy() {}
}
