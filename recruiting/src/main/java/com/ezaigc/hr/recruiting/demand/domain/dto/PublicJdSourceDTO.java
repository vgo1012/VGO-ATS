package com.ezaigc.hr.recruiting.demand.domain.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class PublicJdSourceDTO {
  private String id;
  private String code;
  private String title;
  private String workRegionName;
  /** 逻辑 JD 当前所属人；系统公共来源可未分配。 */
  private String ownerUserId;
  private String ownerUserName;
  private String ownerDepartmentId;
  private String ownerDepartmentName;
  private Integer currentVersionNo;
  private String currentVersionId;
  private String rawText;
  private Instant createTime;
}
