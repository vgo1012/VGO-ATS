package com.ezaigc.hr.common.core.security;

import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CurrentActor {

    /**
     * 用户 ID。
     */
    private String userId;
    /**
     * 权限列表。
     */
    private Set<String> permissions;
    /**
     * 超级管理员。
     */
    private boolean superAdmin;

    public CurrentActor(String userId, Set<String> permissions, boolean superAdmin) {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        this.userId = userId;
        this.permissions = permissions;
        this.superAdmin = superAdmin;
    }

    public boolean hasPermission(String permission) {
        return superAdmin || permissions.contains(permission);
    }
}
