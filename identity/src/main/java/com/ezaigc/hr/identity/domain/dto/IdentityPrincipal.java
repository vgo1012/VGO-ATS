package com.ezaigc.hr.identity.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
public class IdentityPrincipal implements UserDetails, Serializable {

    /**
     * 用户 ID。
     */
    private String userId;
    /**
     * 用户展示名称。
     */
    private String displayName;
    /**
     * 工作邮箱。
     */
    private String workEmail;
    /**
     * 密码。
     */
    @ToString.Exclude
    private String password;
    /**
     * 用户主部门 ID。
     */
    private String homeDepartmentId;
    /**
     * 角色列表。
     */
    private Set<String> roles;
    /**
     * 权限列表。
     */
    private Set<String> permissions;
    /**
     * 下次登录是否必须修改密码。
     */
    private boolean mustChangePassword;
    /**
     * 技术乐观锁版本号。
     */
    private long version;
    /**
     * 是否启用。
     */
    private boolean enabled;

    public IdentityPrincipal(
            String userId,
            String displayName,
            String workEmail,
            String password,
            String homeDepartmentId,
            Set<String> roles,
            Set<String> permissions,
            boolean mustChangePassword,
            long version,
            boolean enabled) {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        this.userId = userId;
        this.displayName = displayName;
        this.workEmail = workEmail;
        this.password = password;
        this.homeDepartmentId = homeDepartmentId;
        this.roles = roles;
        this.permissions = permissions;
        this.mustChangePassword = mustChangePassword;
        this.version = version;
        this.enabled = enabled;
    }

    /**
     * Java 序列化版本标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    public boolean superAdmin() {
        return roles.contains("SUPER_ADMIN");
    }

    /** 返回当前用户的角色与权限授权集合。 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> roleAuthorities =
                roles.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
        List<GrantedAuthority> permissionAuthorities =
                permissions.stream()
                        .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
                        .toList();
        return Stream.concat(roleAuthorities.stream(), permissionAuthorities.stream())
                .toList();
    }

    /** 返回作为登录名的工作邮箱。 */
    @Override
    public String getUsername() {
        return workEmail;
    }

}
