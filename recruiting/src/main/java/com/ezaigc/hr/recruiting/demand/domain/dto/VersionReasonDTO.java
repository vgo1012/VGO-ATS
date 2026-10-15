package com.ezaigc.hr.recruiting.demand.domain.dto;

import lombok.Data;

@Data
public class VersionReasonDTO {
  /** 技术乐观锁版本号。 */
  private long version;
  /** 操作原因。 */
  private String reason;
}
