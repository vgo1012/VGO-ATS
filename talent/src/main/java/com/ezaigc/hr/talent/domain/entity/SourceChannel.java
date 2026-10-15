package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_source_channel")
public class SourceChannel extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 业务编码。 */
  @TableField("code")
  private String code;
  /** 名称。 */
  @TableField("name")
  private String name;
  /** 渠道类型。 */
  @TableField("channel_type")
  private String channelType;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

}
