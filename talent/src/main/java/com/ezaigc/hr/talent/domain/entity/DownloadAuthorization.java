package com.ezaigc.hr.talent.domain.entity;

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
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_download_authorization")
public class DownloadAuthorization extends StandardVersionedPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 访问令牌哈希值。
     */
    @TableField("token_hash")
    @ToString.Exclude
    private String tokenHash;
    /**
     * 简历版本 ID。
     */
    @TableField("resume_version_id")
    private String resumeVersionId;
    /**
     * 被授权用户 ID。
     */
    @TableField("authorized_user_id")
    private String authorizedUserId;
    /**
     * 过期时间。
     */
    @TableField("expires_at")
    private Instant expiresAt;
    /**
     * 消费时间。
     */
    @TableField("consumed_at")
    private Instant consumedAt;

}
