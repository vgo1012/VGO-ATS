package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_communication")
public class Communication extends StandardVersionedPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 应聘流程 ID。
     */
    @TableField("application_id")
    private String applicationId;
    /**
     * 通知或沟通渠道。
     */
    @TableField("channel")
    private String channel;
    /**
     * 摘要。
     */
    @TableField("summary")
    private String summary;
    /**
     * 结果。
     */
    @TableField("result")
    private String result;
    /**
     * 下一步方案。
     */
    @TableField("next_plan")
    private String nextPlan;
    /**
     * 沟通时间。
     */
    @TableField("communicated_at")
    private Instant communicatedAt;
    /**
     * 记录人用户 ID。
     */
    @TableField("recorded_by_user_id")
    private String recordedByUserId;
}
