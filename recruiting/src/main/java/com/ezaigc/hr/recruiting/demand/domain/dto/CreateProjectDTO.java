package com.ezaigc.hr.recruiting.demand.domain.dto;

import lombok.Data;

@Data
public class CreateProjectDTO {
  /** 业务编码。 */
  private String code;
  /** 名称。 */
  private String name;
}
