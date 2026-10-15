package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class PublicJdSourceResponse {
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
  /** 仅详情接口返回原文。 */
  private String rawText;
  private Instant createTime;
}
