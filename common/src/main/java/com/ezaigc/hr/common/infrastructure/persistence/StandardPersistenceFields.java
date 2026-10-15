package com.ezaigc.hr.common.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 所有平台数据表共享的扩展、备注和统一审计字段。
 */
@Getter
@Setter
public abstract class StandardPersistenceFields {

    /**
     * JSON 扩展信息。
     */
    @TableField("ext")
    private String ext;

    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;

    @TableField(value = "create_id", fill = FieldFill.INSERT)
    private String createId;

    /**
     * 创建名称。
     */
    @TableField("create_name")
    private String createName;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Instant createTime;

    @TableField(value = "update_id", fill = FieldFill.INSERT_UPDATE)
    private String updateId;

    /**
     * 更新名称。
     */
    @TableField("update_name")
    private String updateName;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Instant updateTime;
}
