package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工纠正简历事实的 HTTP 请求。 */
@Data
public class FactCorrectionRequest {
  /** 简历版本 ID。 */
  private @NotBlank @Size(min = 26, max = 26) String resumeVersionId;

  /** 本次追加更正所基于的事实快照 ID，用于并发冲突检测。 */
  private @NotBlank @Size(min = 26, max = 26) String baseFactSnapshotId;

  /** 操作原因。 */
  private @NotBlank @Size(max = 500) String reason;

  /** 事实列表。 */
  private @Valid @NotNull ResumeFacts facts;
}
