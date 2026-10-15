package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("identity_user_account")
public class UserAccountRecord extends StandardPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 用户展示名称。
     */
    @TableField("display_name")
    private String displayName;
    /**
     * 工作邮箱。
     */
    @TableField("work_email")
    private String workEmail;
    /**
     * 用于唯一匹配的规范化邮箱。
     */
    @TableField("normalized_email")
    private String normalizedEmail;
    /**
     * 登录凭据哈希值。
     */
    @TableField("credential_hash")
    @ToString.Exclude
    private String credentialHash;
    /** 当前失败登录窗口内的次数，不参与业务版本。 */
    @TableField("failed_login_count")
    private int failedLoginCount;
    @TableField("failed_login_window_started_at")
    private Instant failedLoginWindowStartedAt;
    @TableField("login_locked_until")
    private Instant loginLockedUntil;
    /**
     * 来源职位名称。
     */
    @TableField("source_title")
    private String sourceTitle;
    /**
     * 用户主部门 ID。
     */
    @TableField("home_department_id")
    private String homeDepartmentId;
    /**
     * 直属领导用户 ID。
     */
    @TableField("direct_leader_user_id")
    private String directLeaderUserId;
    /**
     * 当前状态。
     */
    @TableField("status")
    private String status;
    /**
     * 下次登录是否必须修改密码。
     */
    @TableField("must_change_password")
    private boolean mustChangePassword;
    /**
     * 注册时间。
     */
    @TableField("registered_at")
    private Instant registeredAt;
    /**
     * 凭据最近重置时间。
     */
    @TableField("credential_reset_at")
    private Instant credentialResetAt;
    /**
     * 停用时间。
     */
    @TableField("disabled_at")
    private Instant disabledAt;
    /**
     * 技术乐观锁版本号。
     */
    @TableField("version")
    @Version
    private long version;

    public UserAccountRecord(
            String id,
            String displayName,
            String workEmail,
            String normalizedEmail,
            String credentialHash,
            String homeDepartmentId,
            String status,
            boolean mustChangePassword,
            long version) {
        this.id = id;
        this.displayName = displayName;
        this.workEmail = workEmail;
        this.normalizedEmail = normalizedEmail;
        this.credentialHash = credentialHash;
        this.homeDepartmentId = homeDepartmentId;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
        this.version = version;
    }

    public boolean active() {
        return IdentityActivationStatus.ACTIVE.matches(status);
    }
}
