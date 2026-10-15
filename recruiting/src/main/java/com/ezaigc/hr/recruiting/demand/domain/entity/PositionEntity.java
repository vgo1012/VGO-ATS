package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_client_position")
public class PositionEntity extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;
  /** 项目 ID。 */
  @TableField("project_id")
  private String projectId;
  /** 业务编码。 */
  @TableField("code")
  private String code;
  /** 名称。 */
  @TableField("name")
  private String name;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private Long version;

}
