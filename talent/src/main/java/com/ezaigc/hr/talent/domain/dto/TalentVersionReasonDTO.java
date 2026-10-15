package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TalentVersionReasonDTO {
  /** 技术乐观锁版本号。 */
  private long version;

  /** 操作原因。 */
  private String reason;

  /** 可选的已核对简历版本，用于共享库准入。 */
  private String resumeVersionId;

  public TalentVersionReasonDTO(long version, String reason) {
    this(version, reason, null);
  }
}
