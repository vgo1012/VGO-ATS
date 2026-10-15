package com.ezaigc.hr.bootstrap.config;

import com.ezaigc.hr.common.core.util.CommonUtils;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hr.bootstrap-admin")
@Data
@NoArgsConstructor
public class IdentityBootstrapAdminProperties {

    private static final int MAX_DEPARTMENT_CODE_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 16;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final Pattern DEPARTMENT_CODE_PATTERN =
            Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");

    /** 是否启用一次性初始超级管理员创建。 */
    private boolean enabled;

    /**
     * 部门编码。
     */
    @Size(max = MAX_DEPARTMENT_CODE_LENGTH)
    private String departmentCode;
    /**
     * 部门名称。
     */
    @Size(max = MAX_NAME_LENGTH)
    private String departmentName;
    /**
     * 用户展示名称。
     */
    @Size(max = MAX_NAME_LENGTH)
    private String displayName;
    /**
     * 工作邮箱。
     */
    @Email
    @Size(max = MAX_EMAIL_LENGTH)
    @ToString.Exclude
    private String workEmail;
    /**
     * 密码。
     */
    @Size(max = MAX_PASSWORD_LENGTH)
    @ToString.Exclude
    private String password;

    /**
     * 启用初始化时拒绝缺失或弱配置；关闭时允许敏感字段保持空值。
     */
    @AssertTrue(message = "bootstrap administrator configuration must be complete when enabled")
    public boolean isConfigurationValidWhenEnabled() {
        if (!enabled) {
            return true;
        }
        return !CommonUtils.isBlank(departmentCode)
                && DEPARTMENT_CODE_PATTERN.matcher(departmentCode).matches()
                && !CommonUtils.isBlank(departmentName)
                && !CommonUtils.isBlank(displayName)
                && !CommonUtils.isBlank(workEmail)
                && !CommonUtils.isBlank(password)
                && password.length() >= MIN_PASSWORD_LENGTH;
    }

}
