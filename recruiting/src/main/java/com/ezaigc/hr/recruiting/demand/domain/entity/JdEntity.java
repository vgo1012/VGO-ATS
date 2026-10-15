package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_jd")
public class JdEntity extends StandardVersionedPersistenceFields {
  /** 当前岗位类型，历史 JD 可空；调整不重新编号。 */
  @TableField("job_type_code") private String jobTypeCode;
  /** 当前工作区域，历史 JD 可空；调整不重新编号。 */
  @TableField("work_region_code") private String workRegionCode;

  /** 系统生成的稳定编号。 */
  @TableField("code")
  private String code;
  /** 同名岗位的区分说明。 */
  @TableField("distinction")
  private String distinction;
  @TableField("public_visible")
  private boolean publicVisible;
  @TableField("content_shared")
  private boolean contentShared;
  @TableField("recruitment_type")
  private String recruitmentType;
  /** 招聘编制；历史自动来源可空，补充前不能新建招聘流程。 */
  @TableField("headcount")
  private Integer headcount;
  /** 招聘周期（天）；历史自动来源可空。 */
  @TableField("recruitment_cycle_days")
  private Integer recruitmentCycleDays;
  /** 当前已被真实入职占用的编制。 */
  @TableField(exist = false)
  private int occupiedHeadcount;
  @TableField(exist = false)
  private int onboardedHeadcount;
  @TableField(exist = false)
  private String workRegionName;
  /** 逻辑删除后仍保留历史版本和来源。 */
  @TableField("deleted")
  private boolean deleted;

  @TableField(exist = false)
  private String currentParseStatus;
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 招聘需求 ID。 */
  @TableField("demand_id")
  private String demandId;

  /** 未关联正式需求的来源草稿归属部门。 */
  @TableField("source_owner_department_id")
  private String sourceOwnerDepartmentId;

  /** 来源正文和标题的指纹，限同一收件人去重。 */
  @TableField("source_fingerprint")
  private String sourceFingerprint;

  /** 非空表示应用部门来源时冻结的原始 JD 版本。 */
  @TableField("applied_from_jd_version_id")
  private String appliedFromJdVersionId;

  /** 招聘需求编码。 */
  @TableField(exist = false)
  private String demandCode;

  /** 标题。 */
  @TableField("title")
  private String title;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 当前业务版本号。 */
  @TableField("current_version_no")
  private Integer currentVersionNo;

  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;

  /** 客户名称。 */
  @TableField(exist = false)
  private String clientName;

  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;

  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;

  /** 锁定读中的 JD 当前归属原值，避免历史回退字段扩大部门来源权限。 */
  @TableField(exist = false)
  private String catalogOwnerDepartmentId;

  /** 关闭操作人用户 ID。 */
  @TableField("closed_by_user_id")
  private String closedByUserId;
  /** 关闭时间。 */
  @TableField("closed_at")
  private Instant closedAt;

}
