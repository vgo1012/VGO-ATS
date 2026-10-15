package com.ezaigc.hr.identity.api;

import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.LinkedHashSet;

@Data
@NoArgsConstructor
public class CurrentActorSnapshot {

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
     * 用户主部门 ID。
     */
    private String homeDepartmentId;

    private Set<String> supportDepartmentIds;

    private Set<String> managedUserIds;

    private Set<String> explicitlyGrantedDepartmentIds;
    /**
     * 角色列表。
     */
    private Set<String> roles;
    /**
     * 权限列表。
     */
    private Set<String> permissions;
    /**
     * 公司范围。
     */
    private boolean companyScope;
    /**
     * 超级管理员。
     */
    private boolean superAdmin;

    public CurrentActorSnapshot(
            String userId,
            String displayName,
            String workEmail,
            String homeDepartmentId,
            Set<String> supportDepartmentIds,
            Set<String> managedUserIds,
            Set<String> explicitlyGrantedDepartmentIds,
            Set<String> roles,
            Set<String> permissions,
            boolean companyScope,
            boolean superAdmin) {
        supportDepartmentIds = Set.copyOf(supportDepartmentIds);
        managedUserIds = Set.copyOf(managedUserIds);
        explicitlyGrantedDepartmentIds = Set.copyOf(explicitlyGrantedDepartmentIds);
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        this.userId = userId;
        this.displayName = displayName;
        this.workEmail = workEmail;
        this.homeDepartmentId = homeDepartmentId;
        this.supportDepartmentIds = supportDepartmentIds;
        this.managedUserIds = managedUserIds;
        this.explicitlyGrantedDepartmentIds = explicitlyGrantedDepartmentIds;
        this.roles = roles;
        this.permissions = permissions;
        this.companyScope = companyScope;
        this.superAdmin = superAdmin;
    }

    public boolean hasPermission(String permissionCode) {
        return superAdmin || permissions.contains(permissionCode);
    }

    /**
     * 判断当前用户是否拥有不受部门限制的公司级可见范围。
     */
    public boolean hasCompanyWideScope() {
        return superAdmin || companyScope || (roles != null && roles.contains("HR_DIRECTOR"));
    }

    /**
     * 日常运营页面不因招聘负责人管理链扩大数据范围；人力负责人和管理员仍由公司范围放行。
     */
    public Set<String> getOperationalManagedUserIds() {
        return Set.of();
    }

    /**
     * 供列表逐项鉴权复用的运营范围快照，保留本人、显式协作、权限与公司范围。
     */
    public CurrentActorSnapshot operationalScope() {
        if (managedUserIds == null || managedUserIds.isEmpty()) {
            return this;
        }
        return new CurrentActorSnapshot(
                userId,
                displayName,
                workEmail,
                homeDepartmentId,
                supportDepartmentIds,
                Set.of(),
                explicitlyGrantedDepartmentIds,
                roles,
                permissions,
                companyScope,
                superAdmin);
    }

    /** 部门任职不授予业务数据访问；列表统一使用归属人与管理链。 */
    public Set<String> visibleDepartmentIds() {
        return Set.of();
    }

    /** 仅用于创建和分配时校验任职部门，不能用于读取他人的业务数据。 */
    public Set<String> affiliatedDepartmentIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (homeDepartmentId != null) result.add(homeDepartmentId);
        result.addAll(supportDepartmentIds);
        return Set.copyOf(result);
    }

    /** 兼容各业务查询入口；历史部门授权不再扩大私有数据范围。 */
    public Set<String> visibleDepartmentIdsFor(String objectType) {
        return Set.of();
    }
}
