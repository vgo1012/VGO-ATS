package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class JdResponse {
  /** 当前岗位类型三位码；历史 JD 可空。 */
  private String jobTypeCode;
  /** 当前工作区域三位码；历史 JD 可空。 */
  private String workRegionCode;

  /** 系统生成的稳定编号。 */
  private String code;
  /** 同名岗位的区分说明。 */
  private String distinction;
  private boolean publicVisible;
  private boolean contentShared;
  private String appliedFromJdVersionId;
  private String recruitmentType;
  private Integer headcount;
  private Integer recruitmentCycleDays;
  private int occupiedHeadcount;
  /** 已真实入职人数；当前与 occupiedHeadcount 口径一致。 */
  private int onboardedHeadcount;
  private String workRegionName;
  private Integer remainingHeadcount;
  /** 逻辑删除后仍保留历史版本和来源。 */
  private boolean deleted;
  private String version;

  private String currentParseStatus;
  /** 主键 ID。 */
  private String id;
  /** 招聘需求 ID。 */
  private String demandId;
  /** 招聘需求编码。 */
  private String demandCode;
  /** 标题。 */
  private String title;
  /** 当前状态。 */
  private String status;
  /** 当前业务版本号。 */
  private Integer currentVersionNo;
  /** 客户 ID。 */
  private String clientId;
  /** 客户名称。 */
  private String clientName;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  /** 创建时间。 */
  private Instant createTime;
}
