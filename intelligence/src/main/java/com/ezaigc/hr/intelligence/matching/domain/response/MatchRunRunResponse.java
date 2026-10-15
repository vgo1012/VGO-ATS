package com.ezaigc.hr.intelligence.matching.domain.response;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRunRunResponse {
  /** 主键 ID。 */
  private String id;
  /** 异步任务 ID。 */
  private String taskId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** JD 事实快照 ID。 */
  private String jdFactSnapshotId;
  /** 匹配方案版本 ID。 */
  private String planVersionId;
  /** 权重方案版本 ID。 */
  private String weightProfileVersionId;
  /** 个人偏好版本 ID。 */
  private String personalPreferenceVersionId;
  /** 候选人选择快照 ID。 */
  private String selectionSnapshotId;
  /** 结果排序模式。 */
  private String sortMode;
  /** 当前状态。 */
  private String status;
  /** 总记录数。 */
  private int totalCount;
  /** 成功记录数。 */
  private int succeededCount;
  /** 失败记录数。 */
  private int failedCount;
  /** 错误编码。 */
  private String errorCode;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 创建时间。 */
  private Instant createTime;
  /** 开始处理时间。 */
  private Instant startedAt;
  /** 完成处理时间。 */
  private Instant finishedAt;
  /** 更新时间。 */
  private Instant updateTime;
  /** 评分运行归属人。 */
  private String ownerUserId;

  public static MatchRunRunResponse from(MatchRun v) {
    return new MatchRunRunResponse(
        v.getId(),
        v.getTaskId(),
        v.getJdVersionId(),
        v.getJdFactSnapshotId(),
        v.getPlanVersionId(),
        v.getWeightProfileVersionId(),
        v.getPersonalPreferenceVersionId(),
        v.getSelectionSnapshotId(),
        v.getSortMode(),
        v.getStatus(),
        v.getTotalCount(),
        v.getSucceededCount(),
        v.getFailedCount(),
        v.getErrorCode(),
        format(v.getVersion()),
        v.getCreateTime(),
        v.getStartedAt(),
        v.getFinishedAt(),
        v.getUpdateTime(), v.getOwnerUserId());
  }
}
