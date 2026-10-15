package com.ezaigc.hr.recruiting.demand.domain.entity;

import lombok.Data;

/** 当前有效 JD 的邮件路由查询投影。 */
@Data
public class MailJdRoutingRow {
  private String jdId;
  private String jdVersionId;
  private String code;
  private String title;
  private int versionNo;
  private String ownerUserId;
  private String ownerDepartmentId;
  private String workRegionName;
  private String factsJson;
}
