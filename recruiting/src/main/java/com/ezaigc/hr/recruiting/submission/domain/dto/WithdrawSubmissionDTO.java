package com.ezaigc.hr.recruiting.submission.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WithdrawSubmissionDTO {
  /** 操作原因。 */
  private String reason;
  /** 技术乐观锁版本号。 */
  private long version;
}
