package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("matching_match_run_attempt")
public class MatchRunAttempt extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 匹配运行任务 ID。 */
  @TableField("match_run_id")
  private String matchRunId;
  /** 尝试序号。 */
  @TableField("attempt_no")
  private Integer attemptNo;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 开始处理时间。 */
  @TableField("started_at")
  private Instant startedAt;
  /** 完成处理时间。 */
  @TableField("finished_at")
  private Instant finishedAt;

}
