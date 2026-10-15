package com.ezaigc.hr.reporting.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_metric_event")
public class MetricEventEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId("id") private String id;
  /** 事件类型。 */
  @TableField("event_type")
  private String eventType;
  /** 数据来源类型。 */
  @TableField("source_type")
  private String sourceType;
  /** 来源记录 ID。 */
  @TableField("source_id")
  private String sourceId;
  /** 实际操作人用户 ID。 */
  @TableField("actor_user_id")
  private String actorUserId;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 部门 ID。 */
  @TableField("department_id")
  private String departmentId;
  /** 职位描述（JD）ID。 */
  @TableField("jd_id")
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;
  /** 来源渠道 ID。 */
  @TableField("source_channel_id")
  private String sourceChannelId;
  /** 值小数。 */
  @TableField("value_decimal")
  private BigDecimal valueDecimal;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
  /** 采集时间。 */
  @TableField("captured_at")
  private Instant capturedAt;
}
