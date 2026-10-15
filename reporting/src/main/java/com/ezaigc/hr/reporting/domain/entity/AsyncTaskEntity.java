package com.ezaigc.hr.reporting.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_async_task")
public class AsyncTaskEntity extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId("id") private String id;
  /** 创建任务时的请求关联 ID；历史任务允许为空。 */
  @TableField("trace_id") private String traceId;
  /** 任务类型。 */
  @TableField("task_type")
  private String taskType;
  /** 资源类型。 */
  @TableField("resource_type")
  private String resourceType;
  /** 资源 ID。 */
  @TableField("resource_id")
  private String resourceId;
  /** 请求发起人用户 ID。 */
  @TableField("requested_by_user_id")
  private String requestedByUserId;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  /** 查看权限。 */
  @TableField("view_permission")
  private String viewPermission;
  /** 管理权限。 */
  @TableField("manage_permission")
  private String managePermission;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 已尝试次数。 */
  @TableField("attempt_count")
  private int attemptCount;
  /** 最大尝试次数。 */
  @TableField("max_attempts")
  private int maxAttempts;
  /** 任务进度百分比。 */
  @TableField("progress_percent")
  private int progressPercent;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 可安全展示的错误详情。 */
  @TableField("safe_error_detail")
  private String safeErrorDetail;
  /** 失败后是否允许重试。 */
  @TableField("retryable")
  private boolean retryable;
  /** 结果资源 ID。 */
  @TableField("result_resource_id")
  private String resultResourceId;
  /** 幂等键。 */
  @TableField("idempotency_key")
  private String idempotencyKey;
  /** 请求指纹。 */
  @TableField("request_fingerprint")
  private String requestFingerprint;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
  /** 开始处理时间。 */
  @TableField("started_at")
  private Instant startedAt;
  /** 当前处理批次的租约到期时间。 */
  @TableField("lease_until")
  private Instant leaseUntil;
  /** 延迟重试的最早领取时间；空值表示可立即领取。 */
  @TableField("next_retry_at")
  private Instant nextRetryAt;
  /** 完成处理时间。 */
  @TableField("finished_at")
  private Instant finishedAt;

}
