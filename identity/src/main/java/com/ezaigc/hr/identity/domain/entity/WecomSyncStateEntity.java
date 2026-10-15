package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
@TableName("identity_wecom_sync_state")
public class WecomSyncStateEntity extends StandardPersistenceFields {
    @TableId("corp_id")
    private String corpId;
    @TableField(value = "active_batch_id", updateStrategy = FieldStrategy.ALWAYS)
    private String activeBatchId;
    @TableField("published_batch_id")
    private String publishedBatchId;
    @TableField("next_allowed_at")
    private Instant nextAllowedAt;
    @Version
    @TableField("version")
    private int version;
}
