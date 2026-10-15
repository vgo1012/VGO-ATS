package com.ezaigc.hr.recruiting.demand.domain.dto;

import lombok.Data;

@Data
public class CreatePositionDTO {
  /** 客户 ID。 */
  private String clientId;
  /** 项目 ID。 */
  private String projectId;
  /** 业务编码。 */
  private String code;
  /** 名称。 */
  private String name;
}
