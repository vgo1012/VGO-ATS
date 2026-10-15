package com.ezaigc.hr.recruiting.submission.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("submission_idempotency_record")
public class IdempotencyRecord extends StandardVersionedPersistenceFields {
  /** 实际操作人用户 ID。 */
  @TableField("actor_user_id")
  private String actorUserId;
  /** 幂等键。 */
  @TableField("idempotency_key")
  private String idempotencyKey;
  /** 操作编码。 */
  @TableField("action_code")
  private String actionCode;
  /** 请求指纹。 */
  @TableField("request_fingerprint")
  private String requestFingerprint;
  /** 目标记录 ID。 */
  @TableField("target_id")
  private String targetId;
}
