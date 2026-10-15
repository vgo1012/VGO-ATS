package com.ezaigc.hr.identity.application.adapter;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class IdentityAuthorizationAdapter implements AuthorizationService {

    private static final String CURRENT_ACTOR_REQUEST_ATTRIBUTE =
            IdentityAuthorizationAdapter.class.getName() + ".currentActor";

    @Resource
    private ObjectGrantPersistenceService objectGrantPersistenceService;
    @Resource
    private UserSupportDepartmentPersistenceService userSupportDepartmentPersistenceService;

    /**
     * 操作人提供方。
     */
    @Resource
    private CurrentActorProvider actorProvider;

    /**
     * 账户数据映射器。
     */
    @Resource
    private IdentityAccountPersistenceService accountService;

    @Override
    @Transactional(readOnly = true)
    public CurrentActorSnapshot currentActor() {
        return loadCurrentActor();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> recruitmentReportManagedUserIds() {
        CurrentActorSnapshot actor = loadCurrentActor();
        if (!actor.hasPermission("reporting.read-own")) {
            throw forbidden("PERMISSION_REQUIRED", "缺少操作权限：reporting.read-own");
        }
        if (actor.hasCompanyWideScope() || !actor.getRoles().contains("RECRUITMENT_MANAGER")) {
            return Set.of();
        }
        return Set.copyOf(accountService.findReportingManagedUserIds(actor.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public void requirePermission(String permissionCode) {
        if (!loadCurrentActor().hasPermission(permissionCode)) {
            throw forbidden("PERMISSION_REQUIRED", "缺少操作权限：" + permissionCode);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(String permissionCode, AuthorizationTarget target) {
        return hasAccess(loadCurrentActor(), permissionCode, target);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(
            CurrentActorSnapshot actor, String permissionCode, AuthorizationTarget target) {
        return hasAccess(actor, permissionCode, target);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> accessibleObjectIds(
            CurrentActorSnapshot actor, String permissionCode, Collection<AuthorizationTarget> targets) {
        if (targets == null || targets.isEmpty() || !actor.hasPermission(permissionCode)) {
            return Set.of();
        }
        LinkedHashSet<String> accessible = new LinkedHashSet<>();
        if (actor.hasCompanyWideScope()) {
            targets.stream()
                    .map(AuthorizationTarget::getObjectId)
                    .filter(Objects::nonNull)
                    .forEach(accessible::add);
            return Set.copyOf(accessible);
        }

        Map<String, LinkedHashSet<String>> unresolvedByType = new LinkedHashMap<>();
        for (AuthorizationTarget target : targets) {
            String objectId = target.getObjectId();
            if (objectId == null) {
                continue;
            }
            if (intrinsicallyAccessible(actor, target)) {
                accessible.add(objectId);
            } else if (target.getObjectType() != null) {
                unresolvedByType
                        .computeIfAbsent(target.getObjectType(), ignored -> new LinkedHashSet<>())
                        .add(objectId);
            }
        }
        unresolvedByType.forEach(
                (objectType, objectIds) ->
                        accessible.addAll(
                                objectGrantPersistenceService.findGrantedObjectIds(
                                        actor.getUserId(), objectType, objectIds, permissionCode)));
        return Set.copyOf(accessible);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAccess(String permissionCode, AuthorizationTarget target) {
        if (!hasAccess(loadCurrentActor(), permissionCode, target)) {
            throw forbidden("DATA_SCOPE_FORBIDDEN", "当前账号无权访问该业务对象");
        }
    }

    private CurrentActorSnapshot loadCurrentActor() {
        CurrentActor actor =
                actorProvider
                        .currentActor()
                        .orElseThrow(
                                () ->
                                        new PlatformException(
                                                HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "登录会话已失效", true));
        ServletRequestAttributes requestAttributes = currentRequestAttributes();
        Authentication authentication = cacheableAuthentication(actor);
        if (requestAttributes != null && authentication != null) {
            Object cached =
                    requestAttributes.getAttribute(
                            CURRENT_ACTOR_REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof RequestActorSnapshot requestSnapshot
                    && requestSnapshot.matches(authentication, actor.getUserId())) {
                return requestSnapshot.snapshot();
            }
        }

        CurrentActorSnapshot snapshot = loadCurrentActor(actor);
        if (requestAttributes != null
                && authentication != null
                && isCurrentAuthentication(authentication, actor.getUserId())) {
            requestAttributes.setAttribute(
                    CURRENT_ACTOR_REQUEST_ATTRIBUTE,
                    new RequestActorSnapshot(
                            authentication,
                            authentication.getPrincipal(),
                            actor.getUserId(),
                            snapshot),
                    RequestAttributes.SCOPE_REQUEST);
        }
        return snapshot;
    }

    private CurrentActorSnapshot loadCurrentActor(CurrentActor actor) {
        UserAccountRecord account = accountService.findById(actor.getUserId());
        if (account == null || !account.active()) {
            throw new PlatformException(HttpStatus.UNAUTHORIZED, "SESSION_INVALIDATED", "登录会话已失效", true);
        }
        Set<String> roles = Set.copyOf(accountService.findRoleCodes(actor.getUserId()));
        return new CurrentActorSnapshot(
                account.getId(),
                account.getDisplayName(),
                account.getWorkEmail(),
                account.getHomeDepartmentId(),
                Set.copyOf(
                        userSupportDepartmentPersistenceService.findSupportDepartmentIds(actor.getUserId())),
                roles.contains("RECRUITMENT_MANAGER")
                        ? Set.copyOf(accountService.findManagedUserIds(actor.getUserId())) : Set.of(),
                Set.of(),
                roles,
                actor.getPermissions(),
                roles.contains("HR_DIRECTOR") || actor.isSuperAdmin(),
                actor.isSuperAdmin());
    }

    private static ServletRequestAttributes currentRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes
                : null;
    }

    private static Authentication cacheableAuthentication(CurrentActor actor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return isCurrentAuthentication(authentication, actor.getUserId()) ? authentication : null;
    }

    private static boolean isCurrentAuthentication(
            Authentication authentication, String expectedUserId) {
        return authentication != null
                && authentication == SecurityContextHolder.getContext().getAuthentication()
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof IdentityPrincipal principal
                && expectedUserId.equals(principal.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> grantedObjectIds(String permissionCode, String objectType, String afterObjectId) {
        var actor = loadCurrentActor();
        if (!actor.hasPermission(permissionCode)) {
            throw forbidden("PERMISSION_REQUIRED", "缺少操作权限：" + permissionCode);
        }
        return objectGrantPersistenceService.findGrantedObjectIdPage(
                actor.getUserId(), objectType, permissionCode, afterObjectId);
    }

    private boolean hasAccess(
            CurrentActorSnapshot actor, String permissionCode, AuthorizationTarget target) {
        if (!actor.hasPermission(permissionCode)) {
            return false;
        }
        if (actor.hasCompanyWideScope()) {
            return true;
        }
        if (intrinsicallyAccessible(actor, target)) {
            return true;
        }
        return target.getObjectType() != null
                && target.getObjectId() != null
                && objectGrantPersistenceService.countObjectGrant(
                actor.getUserId(), target.getObjectType(), target.getObjectId(), permissionCode)
                > 0;
    }

    private static PlatformException forbidden(String code, String detail) {
        return new PlatformException(HttpStatus.FORBIDDEN, code, detail, false);
    }

    private static boolean intrinsicallyAccessible(
            CurrentActorSnapshot actor, AuthorizationTarget target) {
        return actor.getUserId().equals(target.getOwnerUserId())
                || target.getCollaboratorUserIds().contains(actor.getUserId())
                || actor.getManagedUserIds().contains(target.getOwnerUserId())
                || actor.visibleDepartmentIdsFor(target.getObjectType()).contains(target.getDepartmentId());
    }

    private static final class RequestActorSnapshot {
        private final Authentication authentication;
        private final Object principal;
        private final String userId;
        private final CurrentActorSnapshot snapshot;

        private RequestActorSnapshot(Authentication authentication, Object principal,
                String userId, CurrentActorSnapshot snapshot) {
            this.authentication = authentication;
            this.principal = principal;
            this.userId = userId;
            this.snapshot = snapshot;
        }

        private CurrentActorSnapshot snapshot() {
            return snapshot;
        }

        private boolean matches(Authentication currentAuthentication, String currentUserId) {
            return authentication == currentAuthentication
                    && principal == currentAuthentication.getPrincipal()
                    && userId.equals(currentUserId);
        }
    }
}
