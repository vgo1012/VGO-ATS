package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_resume_fact_snapshot")
public class FactSnapshot extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 解析快照 ID。 */
  @TableField("parse_snapshot_id")
  private String parseSnapshotId;
  @TableField("previous_snapshot_id")
  private String previousSnapshotId;
  /** 快照版本。 */
  @TableField("snapshot_version")
  private int snapshotVersion;
  /** 数据来源类型。 */
  @TableField("source_type")
  private String sourceType;
  /** 当前记录唯一标记。 */
  @TableField("current_marker")
  private String currentMarker;
  /** 姓名。 */
  @TableField("full_name")
  private String fullName;
  /** 邮箱地址。 */
  @TableField("email")
  @ToString.Exclude
  private String email;
  /** 联系电话。 */
  @TableField("phone")
  @ToString.Exclude
  private String phone;
  /** 当前角色。 */
  @TableField("current_role")
  private String currentRole;
  /** 所在城市。 */
  @TableField("city")
  private String city;
  /** 年龄（仅保存明确证据）。 */
  @TableField("age")
  private Integer age;
  /** 性别（仅保存明确证据）。 */
  @TableField("gender")
  private String gender;
  /** 求职意向。 */
  @TableField("job_intention")
  private String jobIntention;
  /** 更正操作人用户 ID。 */
  @TableField("corrected_by_user_id")
  private String correctedByUserId;
  /** 更正原因。 */
  @TableField("correction_reason")
  private String correctionReason;

}
