package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_jd_version")
public class JdVersionEntity extends StandardVersionedPersistenceFields {
  /** 冻结版本对应的客户和招聘类型，更新 JD 不回写历史。 */
  @TableField("client_id")
  private String clientId;
  @TableField("recruitment_type")
  private String recruitmentType;
  /** 创建版本时冻结的工作区域；后续编辑 JD 不回写历史流程。 */
  @TableField("work_region_code")
  private String workRegionCode;
  /** 内容去重按原始归属人隔离，更新原文仍保留旧指纹。 */
  @TableField("content_owner_user_id")
  private String contentOwnerUserId;
  @TableField("content_fingerprint")
  private String contentFingerprint;
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 职位描述（JD）ID。 */
  @TableField("jd_id")
  private String jdId;
  /** 查询时补充的稳定岗位编号，不写入版本快照。 */
  @TableField(exist = false)
  private String jdCode;
  /** 业务版本号。 */
  @TableField("version_no")
  private Integer versionNo;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 原始文本内容。 */
  @TableField("raw_text")
  private String rawText;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 数据来源类型。 */
  @TableField("source_type")
  private String sourceType;

}
