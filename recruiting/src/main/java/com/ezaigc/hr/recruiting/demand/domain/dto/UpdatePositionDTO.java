package com.ezaigc.hr.recruiting.demand.domain.dto;

import lombok.Data;

@Data
public class UpdatePositionDTO {
  /** 项目 ID。 */
  private String projectId;
  /** 名称。 */
  private String name;
  /** 技术乐观锁版本号。 */
  private long version;
}
