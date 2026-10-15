package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_candidate_engagement_lock")
public class EngagementLock extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  /** 联系人名称。 */
  @TableField("contact_name")
  private String contactName;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 获得时间。 */
  @TableField("acquired_at")
  private Instant acquiredAt;
  /** 过期时间。 */
  @TableField("expires_at")
  private Instant expiresAt;
  /** 最近进度类型。 */
  @TableField("last_progress_type")
  private String lastProgressType;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

}
