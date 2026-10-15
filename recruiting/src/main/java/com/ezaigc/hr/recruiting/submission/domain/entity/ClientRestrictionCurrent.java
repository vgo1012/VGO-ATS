package com.ezaigc.hr.recruiting.submission.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("submission_client_restriction_current")
public class ClientRestrictionCurrent extends StandardVersionedPersistenceFields {
  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 业务记录 ID。 */
  @TableField("record_id")
  private String recordId;
  /** 是否处于启用状态。 */
  @TableField("active")
  private boolean active;
  /** 过期时间。 */
  @TableField("expires_at")
  private Instant expiresAt;

}
