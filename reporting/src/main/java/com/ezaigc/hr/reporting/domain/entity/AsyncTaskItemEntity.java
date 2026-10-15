package com.ezaigc.hr.reporting.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_task_item")
public class AsyncTaskItemEntity extends StandardVersionedPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId("id")
    private String id;
    /**
     * 异步任务 ID。
     */
    @TableField("task_id")
    private String taskId;
    /**
     * 任务条目类型。
     */
    @TableField("item_type")
    private String itemType;
    /**
     * 任务条目引用。
     */
    @TableField("item_reference")
    private String itemReference;
    /**
     * 用户展示名称。
     */
    @TableField("display_name")
    private String displayName;
    /**
     * 当前状态。
     */
    @TableField("status")
    private String status;
    /**
     * 错误编码。
     */
    @TableField("error_code")
    private String errorCode;
    /**
     * 可安全展示的错误详情。
     */
    @TableField("safe_error_detail")
    private String safeErrorDetail;

}
