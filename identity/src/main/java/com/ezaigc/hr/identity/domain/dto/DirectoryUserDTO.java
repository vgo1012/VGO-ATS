package com.ezaigc.hr.identity.domain.dto;

import lombok.Data;

/** 业务选人目录的最小查询投影，不加载账号凭据及管理字段。 */
@Data
public class DirectoryUserDTO {
  private String id;
  private String displayName;
  private String homeDepartmentId;
  private String status;
}
