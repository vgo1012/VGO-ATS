package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("matching_match_run")
public class MatchRun extends StandardPersistenceFields {
  /** 评分来源版本，隔离历史规则评分与 AI 判断，禁止混排。 */
  @TableField("scoring_version")
  private String scoringVersion = AiScoringConstants.ALGORITHM_VERSION;
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 异步任务 ID。 */
  @TableField("task_id")
  private String taskId;
  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** JD 事实快照 ID。 */
  @TableField("jd_fact_snapshot_id")
  private String jdFactSnapshotId;
  /** 匹配方案版本 ID。 */
  @TableField("plan_version_id")
  private String planVersionId;
  /** 受控条件方案版本 ID。 */
  @TableField("criterion_plan_version_id")
  private String criterionPlanVersionId;
  /** 权重方案版本 ID。 */
  @TableField("weight_profile_version_id")
  private String weightProfileVersionId;
  /** 个人偏好版本 ID。 */
  @TableField("personal_preference_version_id")
  private String personalPreferenceVersionId;
  /** 公司默认评分或用户自定义筛选。 */
  @TableField("run_purpose")
  private String runPurpose;
  /** 候选人选择快照 ID。 */
  @TableField("selection_snapshot_id")
  private String selectionSnapshotId;
  /** 已选受控条件 ID 列表（JSON）。 */
  @TableField("selected_criterion_ids_json")
  private String selectedCriterionIdsJson;
  /** 结果排序模式。 */
  @TableField("sort_mode")
  private String sortMode;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 总记录数。 */
  @TableField("total_count")
  private int totalCount;
  /** 成功记录数。 */
  @TableField("succeeded_count")
  private int succeededCount;
  /** 失败记录数。 */
  @TableField("failed_count")
  private int failedCount;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

  /** 开始处理时间。 */
  @TableField("started_at")
  private Instant startedAt;
  /** 完成处理时间。 */
  @TableField("finished_at")
  private Instant finishedAt;

}
