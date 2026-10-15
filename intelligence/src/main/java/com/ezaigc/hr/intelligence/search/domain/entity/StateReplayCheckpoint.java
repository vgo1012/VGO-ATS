package com.ezaigc.hr.intelligence.search.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物理索引当前状态回放进度，与事实时间修复游标独立。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("search_state_replay_checkpoint")
public class StateReplayCheckpoint extends StandardPersistenceFields {
  @TableId(value = "projection_name", type = IdType.INPUT)
  private String projectionName;
  /** 此回放游标绑定的物理索引。 */
  @TableField("index_name")
  private String indexName;
  /** 最近已成功回放的候选人 ID，不能与事实快照 ID 混用。 */
  @TableField("last_candidate_id")
  private String lastCandidateId;
  /** 本轮已扫描到末尾；后续增量由同事务持久任务负责。 */
  @TableField("completed")
  private boolean completed;
  /** 已完成页面中的候选人总数。 */
  @TableField("projected_count")
  private long projectedCount;
  /** 跨进程页面竞争的技术版本。 */
  @Version
  @TableField("version")
  private long version;
}
