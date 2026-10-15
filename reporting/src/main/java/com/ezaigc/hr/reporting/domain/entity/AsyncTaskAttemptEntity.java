package com.ezaigc.hr.reporting.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 异步任务执行尝试持久化投影。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_async_task_attempt")
public class AsyncTaskAttemptEntity extends StandardVersionedPersistenceFields {

    /** 主键 ID。 */
    @TableId("id")
    private String id;
    /** 异步任务 ID。 */
    @TableField("task_id")
    private String taskId;
    /** 尝试序号。 */
    @TableField("attempt_no")
    private int attemptNo;
    /** 当前状态。 */
    @TableField("status")
    private String status;
    /** 错误编码。 */
    @TableField("error_code")
    private String errorCode;
    /** 可安全展示的错误详情。 */
    @TableField("safe_error_detail")
    private String safeErrorDetail;
    /** 开始处理时间。 */
    @TableField("started_at")
    private Instant startedAt;
    /** 完成处理时间。 */
    @TableField("finished_at")
    private Instant finishedAt;

}
