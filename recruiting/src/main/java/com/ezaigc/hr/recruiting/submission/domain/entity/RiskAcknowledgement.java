package com.ezaigc.hr.recruiting.submission.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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
@TableName("submission_risk_acknowledgement")
public class RiskAcknowledgement extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 客户推荐记录 ID。 */
  @TableField("submission_id")
  private String submissionId;
  /** 风险编码。 */
  @TableField("risk_code")
  private String riskCode;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 风险确认人用户 ID。 */
  @TableField("acknowledged_by_user_id")
  private String acknowledgedByUserId;
}
