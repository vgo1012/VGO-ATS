package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** JD 编号维度选项；发布后名称与两位码不变，不提供删除入口。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_jd_code_option")
public class JdCodeOptionEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT) private String id;
  /** 字典类型：JOB_TYPE 或 REGION。 */
  @TableField("kind") private String kind;
  /** 字典内部两位码 01～99。 */
  @TableField("code") private String code;
  /** 用户维护的名称。 */
  @TableField("name") private String name;
}
