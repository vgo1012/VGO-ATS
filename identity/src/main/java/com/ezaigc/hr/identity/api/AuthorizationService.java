package com.ezaigc.hr.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AuthorizationService {

    CurrentActorSnapshot currentActor();

    /** 招聘报表专用管理范围，包含当前管理链中的停用历史账号；不授予业务对象操作权限。 */
    Set<String> recruitmentReportManagedUserIds();

    void requirePermission(String permissionCode);

    /** 当前操作者的显式对象授权，按对象 ID 去重并游标分页，每批最多 200 项。 */
    List<String> grantedObjectIds(String permissionCode, String objectType, String afterObjectId);

    boolean canAccess(String permissionCode, AuthorizationTarget target);

    boolean canAccess(CurrentActorSnapshot actor, String permissionCode, AuthorizationTarget target);

    /** 批量返回可访问目标 ID，避免列表逐行查询显式授权。 */
    Set<String> accessibleObjectIds(
            CurrentActorSnapshot actor,
            String permissionCode,
            Collection<AuthorizationTarget> targets);

    void requireAccess(String permissionCode, AuthorizationTarget target);
}
