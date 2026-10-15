package com.ezaigc.hr.recruiting.demand.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 稳定的两位码及可读名称。 */
@Data
@AllArgsConstructor
public class JdCodeOptionResponse {
  private String code;
  private String name;
}
