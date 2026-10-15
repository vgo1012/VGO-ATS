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
@TableName("demand_jd_fact_snapshot")
public class JdFactSnapshotEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 模型运行记录 ID。 */
  @TableField("model_run_id")
  private String modelRunId;
  /** 快照版本。 */
  @TableField("snapshot_version")
  private int snapshotVersion;
  /** 数据来源类型。 */
  @TableField("source_type")
  private String sourceType;
  /** 当前记录唯一标记。 */
  @TableField("current_marker")
  private String currentMarker;
  /** 数据结构版本号。 */
  @TableField("schema_version")
  private String schemaVersion;
  /** 结构化事实内容（JSON）。 */
  @TableField("facts_json")
  private String factsJson;

}
