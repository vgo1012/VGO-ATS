package com.ezaigc.hr.identity.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 招聘业务可选择的有效部门，不包含管理配置。 */
@Data
@AllArgsConstructor
public class DirectoryDepartmentResponse {
  private String id;
  private String code;
  private String name;
  private String parentDepartmentId;
  private String status;
}
