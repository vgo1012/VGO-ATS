package com.ezaigc.hr.identity.application.manager;

import static com.ezaigc.hr.common.web.persistence.OptimisticUpdates.requireOne;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import static com.ezaigc.hr.identity.application.support.IdentityInputRules.normalizeCode;
import static com.ezaigc.hr.identity.application.support.IdentityInputRules.normalizeEmail;
import static com.ezaigc.hr.identity.application.support.IdentityInputRules.normalizeRole;
import static com.ezaigc.hr.identity.application.support.IdentityInputRules.requireText;
import static com.ezaigc.hr.identity.application.support.IdentityInputRules.validateNewPassword;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ASSIGNABLE_ROLES_PER_USER;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SUPPORT_DEPARTMENTS_PER_USER;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_USER_QUERY_LENGTH;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.HandoverResource;
import com.ezaigc.hr.identity.api.HandoverResourceContributor;
import com.ezaigc.hr.identity.api.HandoverTransferResult;
import com.ezaigc.hr.identity.application.assembler.IdentityAdministrationAssembler;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.domain.entity.RoleEntity;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.domain.response.AdminUserResponse;
import com.ezaigc.hr.identity.domain.response.CreatedUserResponse;
import com.ezaigc.hr.identity.domain.response.DepartmentResponse;
import com.ezaigc.hr.identity.domain.response.HandoverBatchDetailsResponse;
import com.ezaigc.hr.identity.domain.response.HandoverBatchResponse;
import com.ezaigc.hr.identity.domain.response.HandoverPreviewResponse;
import com.ezaigc.hr.identity.domain.response.ResetCredentialResponse;
import com.ezaigc.hr.identity.domain.response.UserOrganizationAssignmentsResponse;
import com.ezaigc.hr.identity.enums.HandoverBatchStatus;
import com.ezaigc.hr.identity.enums.HandoverItemStatus;
import com.ezaigc.hr.identity.enums.HandoverSelectionMode;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.enums.IdentityRoleCode;
import com.ezaigc.hr.identity.service.AccountHistoryPersistenceService;
import com.ezaigc.hr.identity.service.DepartmentAssignmentQueuePersistenceService;
import com.ezaigc.hr.identity.service.HandoverBatchPersistenceService;
import com.ezaigc.hr.identity.service.HandoverItemPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import com.ezaigc.hr.identity.service.MembershipPersistenceService;
import com.ezaigc.hr.identity.service.RolePersistenceService;
import com.ezaigc.hr.identity.service.UserRolePersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.stream.Collectors;

/**
 * 身份管理应用服务，负责用例编排、权限校验和事务边界。
 */
@Service
public class IdentityAdministrationApplicationManager {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IdentityAdministrationApplicationManager.class);
    private static final SecureRandom CREDENTIAL_RANDOM = new SecureRandom();

    private static String newOneTimeCredential() {
        byte[] bytes = new byte[24];
        CREDENTIAL_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Resource
    private IdentityAdministrationPersistenceService persistence;
    @Resource
    private AccountHistoryPersistenceService accountHistoryPersistenceService;
    @Resource
    private DepartmentAssignmentQueuePersistenceService departmentAssignmentQueuePersistenceService;
    @Resource
    private HandoverBatchPersistenceService handoverBatchPersistenceService;
    @Resource
    private HandoverItemPersistenceService handoverItemPersistenceService;
    @Resource
    private IdentityAuditEventPersistenceService identityAuditEventPersistenceService;
    @Resource
    private MembershipPersistenceService membershipPersistenceService;
    @Resource
    private RolePersistenceService rolePersistenceService;
    @Resource
    private UserRolePersistenceService userRolePersistenceService;
    @Resource
    private UserSupportDepartmentPersistenceService userSupportDepartmentPersistenceService;
    @Resource
    private IdentityAccountPersistenceService accounts;

    @Resource
    private PlatformIdGenerator ids;

    @Resource
    private CurrentActorProvider actorProvider;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private Clock clock;

    @Resource
    private List<HandoverResourceContributor> handoverContributors;
    @Resource
    private IdentityAdministrationAssembler assembler;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> departments() {
        requireSuperAdmin();
        return assembler.toDepartmentResponses(persistence.findDepartments());
    }

    /**
     * 创建部门。
     */
    @Transactional
    public DepartmentResponse createDepartment(String name, String parentId) {
        CurrentActor actor = requireSuperAdmin();
        requireText(name, "部门名称不能为空");
        validateDepartmentParent(null, parentId);
        String id = ids.newId();
        String code = "DEP_" + id;
        Instant now = clock.instant();
        try {
            persistence.insertDepartment(
                    id, code, name.strip(), CommonUtils.stripToNull(parentId), actor.getUserId(), now);
        } catch (DuplicateKeyException exception) {
            throw PlatformExceptions.conflict("DEPARTMENT_CODE_EXISTS", "部门编码已存在");
        }
        audit(
                "DEPARTMENT_CREATED",
                actor.getUserId(),
                "DEPARTMENT",
                id,
                null,
                JsonText.object("code", code),
                null,
                now);
        return assembler.toResponse(persistence.findDepartmentById(id));
    }

    /**
     * 更新部门。
     */
    @Transactional
    public DepartmentResponse updateDepartment(
            String id,
            String name,
            String parentId,
            String assignmentQueueOwnerUserId,
            String status,
            long version) {
        CurrentActor actor = requireSuperAdmin();
        DepartmentRecord current = requireDepartment(id);
        String normalizedStatus = requireActivationStatus(status, "部门状态无效");
        if (IdentityActivationStatus.DELETED.matches(normalizedStatus)) {
            throw PlatformExceptions.badRequest("ENUM_VALUE_INVALID", "部门状态无效");
        }
        requireText(name, "部门名称不能为空");
        validateDepartmentParent(id, parentId);
        validateActiveUser(assignmentQueueOwnerUserId, null);
        if (IdentityActivationStatus.DISABLED.matches(normalizedStatus)
                && (accounts.countActiveUsersInDepartment(id) > 0
                || persistence.countActiveChildDepartments(id) > 0)) {
            throw PlatformExceptions.conflict("DEPARTMENT_HAS_ACTIVE_REFERENCES", "部门仍有启用账号或启用子部门，不能停用");
        }
        int changed =
                persistence.updateDepartment(
                        id,
                        name.strip(),
                        CommonUtils.stripToNull(parentId),
                        normalizedStatus,
                        version,
                        actor.getUserId(),
                        clock.instant());
        requireOne(changed, "DEPARTMENT_VERSION_CONFLICT", "部门已被其他人修改");
        if (CommonUtils.stripToNull(assignmentQueueOwnerUserId) == null) {
            departmentAssignmentQueuePersistenceService.deleteAssignmentQueue(id);
        } else {
            persistence.upsertAssignmentQueue(
                    id, assignmentQueueOwnerUserId.strip(), actor.getUserId(), clock.instant());
        }
        DepartmentRecord updated = persistence.findDepartmentById(id);
        audit(
                "DEPARTMENT_UPDATED",
                actor.getUserId(),
                "DEPARTMENT",
                id,
                null,
                JsonText.object("version", Long.toString(updated.getVersion())),
                null,
                clock.instant());
        return assembler.toResponse(updated);
    }

    /** 删除没有账号、子部门、队列或业务事实引用的部门。 */
    @Transactional
    public void deleteDepartment(String id, long version, String reason) {
        CurrentActor actor = requireSuperAdmin();
        DepartmentRecord current = requireDepartment(id);
        requireText(reason, "删除原因不能为空");
        String normalizedReason = reason.strip();
        Instant now = clock.instant();
        try {
            requireOne(
                    persistence.deleteDepartment(id, version),
                    "DEPARTMENT_VERSION_CONFLICT",
                    "部门已被其他人修改");
        } catch (DataIntegrityViolationException exception) {
            throw PlatformExceptions.conflict(
                    "DEPARTMENT_IN_USE", "部门仍被账号、子部门或业务记录引用，不能删除");
        }
        audit(
                "DEPARTMENT_DELETED",
                actor.getUserId(),
                "DEPARTMENT",
                id,
                normalizedReason,
                null,
                JsonText.object("code", current.getCode()),
                now);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users(String query, String status, int limit) {
        requireSuperAdmin();
        String normalizedStatus = CommonUtils.stripToNull(status);
        if (normalizedStatus != null) {
            normalizedStatus = requireActivationStatus(normalizedStatus, "账号状态无效");
        }
        return assembler.toUserResponses(
                persistence.findUsers(
                        optional(query, "query", MAX_USER_QUERY_LENGTH),
                        normalizedStatus,
                        Math.clamp(limit, 1, 200)));
    }

    @Transactional(readOnly = true)
    public List<String> roles(String userId) {
        requireSuperAdmin();
        requireActiveUser(userId);
        return accounts.findRoleCodes(userId);
    }

    @Transactional(readOnly = true)
    public UserOrganizationAssignmentsResponse organizationAssignments(String userId) {
        requireSuperAdmin();
        requireActiveUser(userId);
        return new UserOrganizationAssignmentsResponse(
                userId,
                Set.copyOf(userSupportDepartmentPersistenceService.findSupportDepartmentIds(userId)),
                Set.copyOf(accounts.findRoleCodes(userId)));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> superAdmins() {
        requireSuperAdmin();
        return assembler.toUserResponses(persistence.findUsersByRole("SUPER_ADMIN"));
    }

    /**
     * 创建用户。
     */
    @Transactional
    public CreatedUserResponse createUser(
            String displayName,
            String workEmail,
            String sourceTitle,
            String departmentId,
            String leaderId,
            Set<String> supportDepartmentIds,
            Set<String> roles,
            String reason) {
        CurrentActor actor = requireSuperAdmin();
        requireText(displayName, "姓名不能为空");
        requireText(reason, "创建原因不能为空");
        String normalizedEmail = normalizeEmail(workEmail);
        Set<String> normalizedRoles = normalizeAssignableRoles(roles);
        if (normalizedRoles.isEmpty()) {
            throw PlatformExceptions.badRequest("ROLE_REQUIRED", "至少分配一个普通角色");
        }
        validateActiveUser(leaderId, null);
        Set<String> normalizedSupport = normalizeSupportDepartmentIds(supportDepartmentIds);
        validateActiveDepartments(departmentId, normalizedSupport);
        Set<String> roleIds = requireActiveRoleIds(normalizedRoles);
        if (accounts.countNormalizedEmail(normalizedEmail) > 0) {
            throw PlatformExceptions.conflict("WORK_EMAIL_EXISTS", "工作邮箱已存在");
        }
        String id = ids.newId();
        String oneTimeCredential = newOneTimeCredential();
        Instant now = clock.instant();
        try {
            accounts.insertUser(
                    id,
                    displayName.strip(),
                    workEmail.strip(),
                    normalizedEmail,
                    passwordEncoder.encode(oneTimeCredential),
                    CommonUtils.stripToNull(sourceTitle),
                    departmentId,
                    CommonUtils.stripToNull(leaderId),
                    actor.getUserId(),
                    now);
            insertOrganizationAssignments(
                    id, departmentId, normalizedSupport, actor.getUserId(), now);
            if (!userRolePersistenceService.insertUserRoles(
                    id, roleIds, actor.getUserId(), reason.strip(), now)) {
                throw new IllegalStateException("账号角色批量写入失败");
            }
        } catch (DuplicateKeyException exception) {
            throw PlatformExceptions.conflict("IDENTITY_DUPLICATE", "邮箱、组织成员关系或角色分配已存在");
        }
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                id,
                "REGISTERED",
                null,
                JsonText.object("status", IdentityActivationStatus.ACTIVE.getCode()),
                reason.strip(),
                actor.getUserId(),
                now,
                0);
        audit(
                "USER_REGISTERED",
                actor.getUserId(),
                "USER_ACCOUNT",
                id,
                reason.strip(),
                JsonText.object("status", IdentityActivationStatus.ACTIVE.getCode()),
                null,
                now);
        return new CreatedUserResponse(id, oneTimeCredential, true, format(0));
    }

    /**
     * 更新用户。
     */
    @Transactional
    public AdminUserResponse updateUser(
            String id,
            String displayName,
            String workEmail,
            String sourceTitle,
            String departmentId,
            String leaderId,
            Set<String> supportDepartmentIds,
            long version,
            String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord current = requireActiveUser(id);
        requireText(displayName, "姓名不能为空");
        requireText(reason, "修改原因不能为空");
        validateActiveUser(leaderId, id);
        validateLeaderChain(id, leaderId);
        Set<String> normalizedSupport = normalizeSupportDepartmentIds(supportDepartmentIds);
        validateActiveDepartments(departmentId, normalizedSupport);
        String normalizedEmail = normalizeEmail(workEmail);
        if (!normalizedEmail.equals(current.getNormalizedEmail())
                && accounts.countNormalizedEmail(normalizedEmail) > 0) {
            throw PlatformExceptions.conflict("WORK_EMAIL_EXISTS", "工作邮箱已存在");
        }
        Instant now = clock.instant();
        int changed;
        try {
            changed =
                    accounts.updateUser(
                            id,
                            displayName.strip(),
                            workEmail.strip(),
                            normalizedEmail,
                            CommonUtils.stripToNull(sourceTitle),
                            departmentId,
                            CommonUtils.stripToNull(leaderId),
                            version,
                            actor.getUserId(),
                            now);
        } catch (DuplicateKeyException exception) {
            throw PlatformExceptions.conflict("WORK_EMAIL_EXISTS", "工作邮箱已存在");
        }
        requireOne(changed, "USER_VERSION_CONFLICT", "账号已被其他人修改或已停用");
        membershipPersistenceService.deleteMembershipByType(id, "HOME");
        membershipPersistenceService.deleteMembershipByType(id, "SUPPORT");
        userSupportDepartmentPersistenceService.deleteSupportDepartments(id);
        insertOrganizationAssignments(id, departmentId, normalizedSupport, actor.getUserId(), now);
        if (!normalizedEmail.equals(current.getNormalizedEmail())) {
            persistence.deleteSessionsByPrincipalName(current.getWorkEmail());
        }
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                id,
                "UPDATED",
                JsonText.object("version", Long.toString(current.getVersion())),
                JsonText.object("version", Long.toString(current.getVersion() + 1)),
                reason.strip(),
                actor.getUserId(),
                now,
                current.getVersion() + 1);
        audit(
                "USER_UPDATED",
                actor.getUserId(),
                "USER_ACCOUNT",
                id,
                reason.strip(),
                null,
                null,
                now);
        AdminUserRecord updated = persistence.findUserById(id);
        if (updated == null) {
            throw PlatformExceptions.notFound("USER_NOT_FOUND", "账号不存在");
        }
        return assembler.toResponse(updated);
    }

    @Transactional
    public ResetCredentialResponse resetCredential(String id, String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireActiveUser(id);
        requireText(reason, "重置原因不能为空");
        String credential = newOneTimeCredential();
        int changed =
                accounts.updateCredential(
                        id, passwordEncoder.encode(credential), true, actor.getUserId(), clock.instant());
        requireOne(changed, "USER_NOT_ACTIVE", "账号不存在或已停用");
        persistence.deleteSessionsByPrincipalName(user.getWorkEmail());
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                id,
                "CREDENTIAL_RESET",
                null,
                JsonText.object("mustChangePassword", "true"),
                reason.strip(),
                actor.getUserId(),
                clock.instant(),
                user.getVersion() + 1);
        audit(
                "USER_CREDENTIAL_RESET",
                actor.getUserId(),
                "USER_ACCOUNT",
                id,
                reason.strip(),
                null,
                null,
                clock.instant());
        return new ResetCredentialResponse(id, credential, true, format(user.getVersion() + 1));
    }

    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        CurrentActor actor = requireActor();
        UserAccountRecord user = requireActiveUser(actor.getUserId());
        if (!passwordEncoder.matches(currentPassword, user.getCredentialHash())) {
            throw PlatformExceptions.badRequest("CURRENT_PASSWORD_INVALID", "当前密码错误");
        }
        validateNewPassword(newPassword);
        if (newPassword.strip().equalsIgnoreCase(user.getNormalizedEmail())) {
            throw PlatformExceptions.badRequest("PASSWORD_EMAIL_NOT_ALLOWED", "新密码不能与企业邮箱相同");
        }
        if (passwordEncoder.matches(newPassword, user.getCredentialHash())) {
            throw PlatformExceptions.badRequest("PASSWORD_REUSE_NOT_ALLOWED", "新密码不能与当前密码相同");
        }
        int changed =
                accounts.updateCredential(
                        user.getId(),
                        passwordEncoder.encode(newPassword),
                        false,
                        actor.getUserId(),
                        clock.instant());
        requireOne(changed, "USER_NOT_ACTIVE", "账号不存在或已停用");
        persistence.deleteSessionsByPrincipalName(user.getWorkEmail());
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                user.getId(),
                "PASSWORD_CHANGED",
                null,
                JsonText.object("mustChangePassword", "false"),
                null,
                actor.getUserId(),
                clock.instant(),
                user.getVersion() + 1);
        audit(
                "PASSWORD_CHANGED",
                actor.getUserId(),
                "USER_ACCOUNT",
                user.getId(),
                null,
                null,
                null,
                clock.instant());
    }

    /**
     * 分配角色。
     */
    @Transactional
    public List<String> assignRole(String userId, String roleCode, String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireActiveUser(userId);
        requireText(reason, "角色变更原因不能为空");
        String normalizedRole = normalizeRole(roleCode);
        if (!IdentityRoleCode.isAssignableCode(normalizedRole)) {
            throw PlatformExceptions.badRequest("ROLE_NOT_ASSIGNABLE", "普通角色接口不能管理该角色");
        }
        if (accounts.findRoleCodes(userId).contains(normalizedRole)) {
            return accounts.findRoleCodes(userId);
        }
        int changed = userRolePersistenceService.insertUserRole(
                userId, requireRoleId(normalizedRole), actor.getUserId(), reason.strip(), clock.instant());
        invalidateRoleSessions(user, actor, changed);
        audit(
                "ROLE_ASSIGNED",
                actor.getUserId(),
                "USER_ACCOUNT",
                userId,
                reason.strip(),
                JsonText.object("role", normalizedRole),
                null,
                clock.instant());
        return accounts.findRoleCodes(userId);
    }

    /**
     * 撤销角色。
     */
    @Transactional
    public List<String> revokeRole(String userId, String roleCode, String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireActiveUser(userId);
        requireText(reason, "角色变更原因不能为空");
        String normalizedRole = normalizeRole(roleCode);
        if (!IdentityRoleCode.isAssignableCode(normalizedRole)) {
            throw PlatformExceptions.badRequest("ROLE_NOT_ASSIGNABLE", "普通角色接口不能管理该角色");
        }
        List<String> currentRoles = accounts.findRoleCodes(userId);
        long ordinaryRoleCount = currentRoles.stream().filter(IdentityRoleCode::isAssignableCode).count();
        if (currentRoles.contains(normalizedRole) && ordinaryRoleCount <= 1) {
            throw PlatformExceptions.conflict("BUSINESS_ROLE_REQUIRED", "启用账号至少保留一个普通业务角色");
        }
        int changed = userRolePersistenceService.deleteUserRole(userId, requireRoleId(normalizedRole));
        invalidateRoleSessions(user, actor, changed);
        audit(
                "ROLE_REVOKED",
                actor.getUserId(),
                "USER_ACCOUNT",
                userId,
                reason.strip(),
                JsonText.object("role", normalizedRole),
                null,
                clock.instant());
        return accounts.findRoleCodes(userId);
    }

    @Transactional
    public List<String> grantSuperAdmin(String userId, String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireActiveUser(userId);
        requireText(reason, "授权原因不能为空");
        if (!accounts.findRoleCodes(userId).contains("SUPER_ADMIN")) {
            int changed = userRolePersistenceService.insertUserRole(
                    userId, requireRoleId("SUPER_ADMIN"), actor.getUserId(), reason.strip(), clock.instant());
            invalidateRoleSessions(user, actor, changed);
            audit(
                    "SUPER_ADMIN_GRANTED",
                    actor.getUserId(),
                    "USER_ACCOUNT",
                    userId,
                    reason.strip(),
                    null,
                    null,
                    clock.instant());
        }
        return accounts.findRoleCodes(userId);
    }

    /**
     * 撤销超级管理员。
     */
    @Transactional
    public List<String> revokeSuperAdmin(String userId, String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireActiveUser(userId);
        requireText(reason, "撤权原因不能为空");
        List<String> activeSuperAdmins = persistence.findActiveSuperAdminUserIdsForUpdate();
        if (activeSuperAdmins.contains(userId) && activeSuperAdmins.size() <= 1) {
            throw PlatformExceptions.conflict("LAST_SUPER_ADMIN_REQUIRED", "系统至少保留一个启用的超级管理员");
        }
        int changed = userRolePersistenceService.deleteUserRole(userId, requireRoleId("SUPER_ADMIN"));
        invalidateRoleSessions(user, actor, changed);
        audit(
                "SUPER_ADMIN_REVOKED",
                actor.getUserId(),
                "USER_ACCOUNT",
                userId,
                reason.strip(),
                null,
                null,
                clock.instant());
        return accounts.findRoleCodes(userId);
    }

    /**
     * 停用用户。
     */
    @Transactional
    public void disableUser(
            String id, long version, String reason, boolean retainHistoricalUnresolved) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireActiveUser(id);
        requireText(reason, "停用原因不能为空");
        if (id.equals(actor.getUserId())) {
            throw PlatformExceptions.conflict("SELF_DISABLE_NOT_ALLOWED", "不能停用当前登录账号");
        }
        if (accounts.findRoleCodes(id).contains("SUPER_ADMIN")) {
            List<String> activeSuperAdmins = persistence.findActiveSuperAdminUserIdsForUpdate();
            if (activeSuperAdmins.contains(id) && activeSuperAdmins.size() <= 1) {
                throw PlatformExceptions.conflict("LAST_SUPER_ADMIN_REQUIRED", "系统至少保留一个启用的超级管理员");
            }
        }
        if (departmentAssignmentQueuePersistenceService.countAssignmentQueuesOwnedBy(id) > 0) {
            throw PlatformExceptions.conflict("ASSIGNMENT_QUEUE_REASSIGNMENT_REQUIRED", "该账号仍负责部门待分配队列，请先改派队列负责人");
        }
        List<HandoverResource> pending = handoverResources(id);
        if (pending.stream().anyMatch(HandoverResource::isMandatory)) {
            throw PlatformExceptions.conflict("MANDATORY_HANDOVER_REQUIRED", "仍有活动业务、可执行任务或有效锁，必须先完成交接");
        }
        if (!pending.isEmpty() && !retainHistoricalUnresolved) {
            throw PlatformExceptions.conflict("HISTORICAL_HANDOVER_CONFIRMATION_REQUIRED", "仍有历史性或不可转交事项，需要带原因确认保留");
        }
        int changed = accounts.disableUser(id, version, actor.getUserId(), clock.instant());
        requireOne(changed, "USER_VERSION_CONFLICT", "账号已被其他人修改或已停用");
        persistence.deleteSessionsByPrincipalName(user.getWorkEmail());
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                id,
                IdentityActivationStatus.DISABLED.getCode(),
                JsonText.object("status", IdentityActivationStatus.ACTIVE.getCode()),
                JsonText.object("status", IdentityActivationStatus.DISABLED.getCode()),
                reason.strip(),
                actor.getUserId(),
                clock.instant(),
                user.getVersion() + 1);
        audit(
                "USER_DISABLED",
                actor.getUserId(),
                "USER_ACCOUNT",
                id,
                reason.strip(),
                null,
                null,
                clock.instant());
    }

    /**
     * 预览交接。
     */
    @Transactional(readOnly = true)
    public HandoverPreviewResponse previewHandover(String sourceUserId, String targetUserId) {
        requireSuperAdmin();
        requireActiveUser(sourceUserId);
        requireActiveUser(targetUserId);
        if (sourceUserId.equals(targetUserId)) {
            throw PlatformExceptions.badRequest("HANDOVER_TARGET_INVALID", "交接目标不能与来源账号相同");
        }
        List<HandoverResource> resources = handoverResources(sourceUserId);
        return new HandoverPreviewResponse(
                sourceUserId,
                targetUserId,
                resources,
                resources.stream().filter(HandoverResource::isMandatory).count(),
                resources.stream().filter(resource -> !resource.isTransferable()).count());
    }

    /**
     * 交接S。
     */
    @Transactional(readOnly = true)
    public List<HandoverBatchResponse> handovers(String sourceUserId) {
        requireSuperAdmin();
        requireActiveUser(sourceUserId);
        return assembler.toBatchResponses(persistence.findHandoversBySource(sourceUserId));
    }

    @Transactional(readOnly = true)
    public HandoverBatchDetailsResponse handover(String sourceUserId, String batchId) {
        requireSuperAdmin();
        HandoverBatchRecord batch = handoverBatchPersistenceService.findHandoverById(batchId);
        if (batch == null || !batch.getSourceUserId().equals(sourceUserId)) {
            throw PlatformExceptions.notFound("HANDOVER_NOT_FOUND", "交接批次不存在");
        }
        return assembler.toDetails(batch, handoverItemPersistenceService.findHandoverItems(batchId));
    }

    /**
     * 执行交接。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public HandoverBatchDetailsResponse executeHandover(
            String sourceUserId,
            String targetUserId,
            String selectionMode,
            Set<String> selectedResourceKeys,
            String reason,
            String rawIdempotencyKey) {
        CurrentActor actor = requireSuperAdmin();
        requireActiveUser(sourceUserId);
        requireActiveUser(targetUserId);
        requireText(reason, "交接原因不能为空");
        if (sourceUserId.equals(targetUserId)) {
            throw PlatformExceptions.badRequest("HANDOVER_TARGET_INVALID", "交接目标不能与来源账号相同");
        }
        String normalizedReason = reason.strip();
        String idempotencyKey = IdempotencyKey.fromHttpHeader(rawIdempotencyKey);
        HandoverBatchRecord existing =
                handoverBatchPersistenceService.findHandoverByIdempotency(actor.getUserId(), idempotencyKey);
        if (existing != null) {
            requireSameHandoverScope(existing, sourceUserId, targetUserId);
            return assembler.toDetails(existing, handoverItemPersistenceService.findHandoverItems(existing.getId()));
        }
        HandoverSelectionMode mode = requireSelectionMode(selectionMode);
        String normalizedMode = mode.name();
        Set<String> selected =
                selectedResourceKeys == null ? Set.of() : Set.copyOf(selectedResourceKeys);
        List<HandoverResource> all = handoverResources(sourceUserId);
        List<HandoverResource> resources =
                mode == HandoverSelectionMode.ALL
                        ? all
                        : all.stream().filter(resource -> selected.contains(resourceKey(resource))).toList();
        if (mode == HandoverSelectionMode.SELECTED) {
            Set<String> known =
                    all.stream()
                            .map(IdentityAdministrationApplicationManager::resourceKey)
                            .collect(Collectors.toSet());
            if (!known.containsAll(selected)) {
                throw PlatformExceptions.badRequest("HANDOVER_SELECTION_INVALID", "勾选的交接事项不存在或已变化，请重新预览");
            }
        }
        String batchId = ids.newId();
        Instant startedAt = clock.instant();
        try {
            transactionTemplate.executeWithoutResult(
                    ignored ->
                            handoverBatchPersistenceService.insertHandoverBatch(
                                    batchId,
                                    sourceUserId,
                                    targetUserId,
                                    normalizedMode,
                                    HandoverBatchStatus.RUNNING.getCode(),
                                    resources.size(),
                                    0,
                                    normalizedReason,
                                    actor.getUserId(),
                                    startedAt,
                                    null,
                                    idempotencyKey));
        } catch (DuplicateKeyException exception) {
            HandoverBatchRecord raced =
                    handoverBatchPersistenceService.findHandoverByIdempotency(actor.getUserId(), idempotencyKey);
            if (raced != null) {
                requireSameHandoverScope(raced, sourceUserId, targetUserId);
                return assembler.toDetails(raced, handoverItemPersistenceService.findHandoverItems(raced.getId()));
            }
            throw exception;
        }

        Map<String, HandoverResourceContributor> contributorsByType = new LinkedHashMap<>();
        handoverContributors.forEach(
                contributor -> contributorsByType.put(contributor.resourceType(), contributor));
        int succeeded = 0;
        int failed = 0;
        int unresolved = 0;
        for (HandoverResource resource : resources) {
            if (!resource.isTransferable()) {
                transactionTemplate.executeWithoutResult(
                        ignored ->
                                persistHandoverItem(
                                        batchId,
                                        resource,
                                        HandoverItemStatus.UNRESOLVED,
                                        "RESOURCE_NOT_TRANSFERABLE",
                                        resource.getUnresolvedReason(),
                                        sourceUserId,
                                        targetUserId,
                                        null));
                unresolved++;
            } else {
                HandoverResourceContributor contributor =
                        contributorsByType.get(resource.getResourceType());
                HandoverTransferResult result =
                        transferHandoverItem(
                                batchId,
                                resource,
                                contributor,
                                sourceUserId,
                                targetUserId,
                                actor.getUserId(),
                                normalizedReason);
                if (result.isSucceeded()) {
                    succeeded++;
                } else {
                    failed++;
                }
            }
        }
        HandoverBatchStatus finalStatus =
                failed == 0 && unresolved == 0
                        ? HandoverBatchStatus.SUCCEEDED
                        : succeeded > 0 ? HandoverBatchStatus.PARTIAL_SUCCESS : HandoverBatchStatus.FAILED;
        int succeededCount = succeeded;
        int failedCount = failed;
        int unresolvedCount = unresolved;
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    requireOne(
                            persistence.completeHandoverBatch(
                                    batchId,
                                    finalStatus.getCode(),
                                    succeededCount,
                                    failedCount,
                                    unresolvedCount,
                                    clock.instant()),
                            "HANDOVER_VERSION_CONFLICT",
                            "交接批次状态已变化，请刷新后重试");
                    audit(
                            "HANDOVER_EXECUTED",
                            actor.getUserId(),
                            "HANDOVER_BATCH",
                            batchId,
                            normalizedReason,
                            JsonText.object("status", finalStatus.getCode()),
                            null,
                            clock.instant());
                });
        return assembler.toDetails(
                handoverBatchPersistenceService.findHandoverById(batchId), handoverItemPersistenceService.findHandoverItems(batchId));
    }

    private HandoverTransferResult transferHandoverItem(
            String batchId,
            HandoverResource resource,
            HandoverResourceContributor contributor,
            String sourceUserId,
            String targetUserId,
            String actorUserId,
            String reason) {
        if (contributor == null) {
            HandoverTransferResult unavailable =
                    HandoverTransferResult.failure("CONTRIBUTOR_NOT_AVAILABLE", "交接处理器当前不可用");
            transactionTemplate.executeWithoutResult(
                    ignored ->
                            persistTransferResult(
                                    batchId, resource, unavailable, sourceUserId, targetUserId));
            return unavailable;
        }
        try {
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            ignored -> {
                                HandoverTransferResult result =
                                        Objects.requireNonNull(
                                                contributor.transfer(
                                                        resource.getResourceId(),
                                                        sourceUserId,
                                                        targetUserId,
                                                        actorUserId,
                                                        reason),
                                                "handover contributor result");
                                persistTransferResult(
                                        batchId, resource, result, sourceUserId, targetUserId);
                                return result;
                            }),
                    "handover transaction result");
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "handover-item-transfer-failed batchId={} resourceType={} resourceId={} errorType={}",
                    batchId,
                    resource.getResourceType(),
                    resource.getResourceId(),
                    exception.getClass().getName(),
                    SafeLogException.from(exception));
            HandoverTransferResult failure =
                    HandoverTransferResult.failure("HANDOVER_ITEM_FAILED", "交接事项处理失败");
            try {
                transactionTemplate.executeWithoutResult(
                        ignored ->
                                persistTransferResult(
                                        batchId, resource, failure, sourceUserId, targetUserId));
            } catch (RuntimeException persistenceFailure) {
                persistenceFailure.addSuppressed(exception);
                throw persistenceFailure;
            }
            return failure;
        }
    }

    private void persistTransferResult(
            String batchId,
            HandoverResource resource,
            HandoverTransferResult result,
            String sourceUserId,
            String targetUserId) {
        persistHandoverItem(
                batchId,
                resource,
                result.isSucceeded() ? HandoverItemStatus.SUCCEEDED : HandoverItemStatus.FAILED,
                result.getErrorCode(),
                result.getErrorMessage(),
                sourceUserId,
                targetUserId,
                result.getOwnershipHistoryId());
    }

    private void persistHandoverItem(
            String batchId,
            HandoverResource resource,
            HandoverItemStatus status,
            String errorCode,
            String errorMessage,
            String sourceUserId,
            String targetUserId,
            String historyId) {
        handoverItemPersistenceService.insertHandoverItem(
                ids.newId(),
                batchId,
                resource.getResourceType(),
                resource.getResourceId(),
                status.getCode(),
                resource.isMandatory(),
                errorCode,
                errorMessage,
                sourceUserId,
                targetUserId,
                historyId,
                clock.instant());
    }

    /**
     * 逻辑删除账号，保留业务引用和审计事实。
     */
    @Transactional
    public void deleteUser(String id, long version, String reason) {
        CurrentActor actor = requireSuperAdmin();
        UserAccountRecord user = requireUser(id);
        requireText(reason, "删除原因不能为空");
        if (id.equals(actor.getUserId())) {
            throw PlatformExceptions.conflict("SELF_DELETE_NOT_ALLOWED", "不能删除当前登录账号");
        }
        if (accounts.findRoleCodes(id).contains("SUPER_ADMIN")) {
            List<String> activeSuperAdmins = persistence.findActiveSuperAdminUserIdsForUpdate();
            if (activeSuperAdmins.contains(id) && activeSuperAdmins.size() <= 1) {
                throw PlatformExceptions.conflict("LAST_SUPER_ADMIN_REQUIRED", "系统至少保留一个启用的超级管理员");
            }
        }
        if (departmentAssignmentQueuePersistenceService.countAssignmentQueuesOwnedBy(id) > 0) {
            throw PlatformExceptions.conflict(
                    "ASSIGNMENT_QUEUE_REASSIGNMENT_REQUIRED", "该账号仍负责部门待分配队列，请先改派队列负责人");
        }
        if (!accounts.findManagedUserIds(id).isEmpty()) {
            throw PlatformExceptions.conflict(
                    "DIRECT_REPORT_REASSIGNMENT_REQUIRED", "该账号仍是启用人员的直属领导，请先调整汇报关系");
        }
        if (!handoverResources(id).isEmpty()) {
            throw PlatformExceptions.conflict(
                    "HANDOVER_REQUIRED_BEFORE_DELETE", "该账号仍有未交接的活动招聘工作，请先完成交接");
        }
        Instant now = clock.instant();
        int changed = accounts.deleteUser(id, version, actor.getUserId(), now);
        requireOne(changed, "USER_VERSION_CONFLICT", "账号已被其他人修改或删除");
        persistence.deleteSessionsByPrincipalName(user.getWorkEmail());
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                id,
                "DELETED",
                JsonText.object("status", user.getStatus()),
                JsonText.object("status", IdentityActivationStatus.DELETED.getCode()),
                reason.strip(),
                actor.getUserId(),
                now,
                user.getVersion() + 1);
        audit(
                "USER_DELETED",
                actor.getUserId(),
                "USER_ACCOUNT",
                id,
                reason.strip(),
                JsonText.object("logical", "true"),
                null,
                now);
    }

    private static void requireSameHandoverScope(
            HandoverBatchRecord batch, String sourceUserId, String targetUserId) {
        if (!Objects.equals(batch.getSourceUserId(), sourceUserId)
                || !Objects.equals(batch.getTargetUserId(), targetUserId)) {
            throw PlatformExceptions.conflict(
                    "IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同交接请求");
        }
    }

    /**
     * 交接资源列表。
     */
    private List<HandoverResource> handoverResources(String sourceUserId) {
        Map<String, HandoverResource> unique = new LinkedHashMap<>();
        for (HandoverResourceContributor contributor : handoverContributors) {
            for (HandoverResource resource : contributor.preview(sourceUserId)) {
                if (!contributor.resourceType().equals(resource.getResourceType())) {
                    throw new IllegalStateException(
                            "Handover contributor returned a mismatched resource type");
                }
                HandoverResource previous = unique.putIfAbsent(resourceKey(resource), resource);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate handover resource: " + resourceKey(resource));
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static String resourceKey(HandoverResource resource) {
        return resource.getResourceType() + ":" + resource.getResourceId();
    }

    @Transactional
    public void provisionInitialSuperAdmin(
            String departmentCode,
            String departmentName,
            String displayName,
            String workEmail,
            String rawSecret) {
        requireText(rawSecret, "初始管理员 Secret 不能为空");
        String normalizedEmail = normalizeEmail(workEmail);
        UserAccountRecord existing = accounts.findByNormalizedEmail(normalizedEmail);
        if (existing != null) {
            if (!accounts.isUsableBootstrappedSuperAdmin(existing.getId())) {
                throw new IllegalStateException("初始管理员邮箱已被非有效初始化超级管理员账号占用");
            }
            return;
        }
        if (rawSecret.length() < 16) {
            throw new IllegalStateException("HR_BOOTSTRAP_ADMIN_PASSWORD 至少需要 16 个字符");
        }
        String actorId = ids.newId();
        Instant now = clock.instant();
        String departmentId;
        String normalizedDepartmentCode = normalizeCode(departmentCode);
        List<DepartmentRecord> departments = persistence.findDepartments();
        departmentId =
                departments.stream()
                        .filter(department -> department.getCode().equals(normalizedDepartmentCode))
                        .map(DepartmentRecord::getId)
                        .findFirst()
                        .orElseGet(
                                () -> {
                                    String id = ids.newId();
                                    persistence.insertDepartment(
                                            id, normalizedDepartmentCode, departmentName.strip(), null, actorId, now);
                                    return id;
                                });
        accounts.insertUser(
                actorId,
                displayName.strip(),
                workEmail.strip(),
                normalizedEmail,
                passwordEncoder.encode(rawSecret),
                "内置初始管理员",
                departmentId,
                null,
                actorId,
                now);
        membershipPersistenceService.insertMembership(actorId, departmentId, "HOME", actorId, now);
        userRolePersistenceService.insertUserRole(
                actorId, requireRoleId("SUPER_ADMIN"), actorId, "部署 Secret 初始化", now);
        accountHistoryPersistenceService.insertAccountHistory(
                ids.newId(),
                actorId,
                "BOOTSTRAPPED",
                null,
                JsonText.object("mustChangePassword", "true"),
                "部署 Secret 初始化",
                actorId,
                now,
                0);
        audit(
                "SUPER_ADMIN_BOOTSTRAPPED",
                actorId,
                "USER_ACCOUNT",
                actorId,
                "部署 Secret 初始化",
                null,
                null,
                now);
    }

    /**
     * 校验部门上级。
     */
    private void validateDepartmentParent(String departmentId, String parentId) {
        String currentId = CommonUtils.stripToNull(parentId);
        Set<String> visited = new LinkedHashSet<>();
        while (currentId != null) {
            if (currentId.equals(departmentId) || !visited.add(currentId)) {
                throw PlatformExceptions.badRequest("DEPARTMENT_CYCLE", "部门层级不能形成环");
            }
            DepartmentRecord parent = requireDepartment(currentId);
            if (!parent.active()) {
                throw PlatformExceptions.badRequest("PARENT_DEPARTMENT_NOT_ACTIVE", "上级部门必须处于启用状态");
            }
            currentId = parent.getParentDepartmentId();
        }
    }

    /**
     * 校验直属领导关系不形成循环。
     */
    private void validateLeaderChain(String userId, String leaderId) {
        String currentId = CommonUtils.stripToNull(leaderId);
        Set<String> visited = new LinkedHashSet<>();
        while (currentId != null) {
            if (currentId.equals(userId) || !visited.add(currentId)) {
                throw PlatformExceptions.badRequest("LEADER_CYCLE", "直属领导关系不能形成环");
            }
            currentId = accounts.findDirectLeaderId(currentId);
        }
    }

    /**
     * 一次读取并校验账号的主部门与支援部门，避免按支援部门数量放大数据库查询。
     */
    private void validateActiveDepartments(
            String homeDepartmentId, Set<String> supportDepartmentIds) {
        if (CommonUtils.stripToNull(homeDepartmentId) == null) {
            throw PlatformExceptions.notFound("DEPARTMENT_NOT_FOUND", "部门不存在");
        }
        Set<String> requestedIds = new LinkedHashSet<>();
        requestedIds.add(homeDepartmentId);
        requestedIds.addAll(supportDepartmentIds);
        List<DepartmentRecord> departments = persistence.listByIds(requestedIds);
        if (departments == null) {
            throw new IllegalStateException("部门批量查询返回 null");
        }
        Map<String, DepartmentRecord> indexed = new LinkedHashMap<>();
        for (DepartmentRecord department : departments) {
            if (department == null
                    || CommonUtils.stripToNull(department.getId()) == null
                    || indexed.putIfAbsent(department.getId(), department) != null) {
                throw new IllegalStateException("部门批量查询返回非法结果");
            }
        }
        for (String requestedId : requestedIds) {
            DepartmentRecord department = indexed.get(requestedId);
            if (department == null) {
                throw PlatformExceptions.notFound("DEPARTMENT_NOT_FOUND", "部门不存在");
            }
            if (!department.active()) {
                throw PlatformExceptions.badRequest("DEPARTMENT_NOT_ACTIVE", "部门未启用");
            }
        }
    }

    private static Set<String> normalizeSupportDepartmentIds(Set<String> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Set.of();
        }
        if (departmentIds.size() > MAX_SUPPORT_DEPARTMENTS_PER_USER) {
            throw PlatformExceptions.badRequest("SUPPORT_DEPARTMENT_LIMIT", "支援部门数量超过限制");
        }
        if (departmentIds.stream().anyMatch(Objects::isNull)) {
            throw PlatformExceptions.badRequest("SUPPORT_DEPARTMENT_INVALID", "支援部门不能为空");
        }
        return Set.copyOf(departmentIds);
    }

    /**
     * 在调用方事务内批量写入账号组织关系；任一批次失败都触发整体回滚。
     */
    private void insertOrganizationAssignments(
            String userId,
            String homeDepartmentId,
            Set<String> supportDepartmentIds,
            String actorId,
            Instant now) {
        Set<String> effectiveSupportDepartmentIds = new LinkedHashSet<>(supportDepartmentIds);
        effectiveSupportDepartmentIds.remove(homeDepartmentId);
        Set<String> immutableSupportDepartmentIds = Set.copyOf(effectiveSupportDepartmentIds);
        if (!membershipPersistenceService.insertOrganizationMemberships(
                userId,
                homeDepartmentId,
                immutableSupportDepartmentIds,
                actorId,
                now)) {
            throw new IllegalStateException("账号组织成员关系批量写入失败");
        }
        if (!immutableSupportDepartmentIds.isEmpty()
                && !userSupportDepartmentPersistenceService.insertSupportDepartments(
                        userId, immutableSupportDepartmentIds, actorId, now)) {
            throw new IllegalStateException("账号支援部门关系批量写入失败");
        }
    }

    /**
     * 校验生效用户。
     */
    private void validateActiveUser(String id, String disallowedId) {
        if (CommonUtils.stripToNull(id) == null) {
            return;
        }
        if (id.equals(disallowedId)) {
            throw PlatformExceptions.badRequest("LEADER_SELF_REFERENCE", "账号不能成为自己的直属领导");
        }
        requireActiveUser(id);
    }

    private UserAccountRecord requireActiveUser(String id) {
        UserAccountRecord user = requireUser(id);
        if (!user.active()) {
            throw PlatformExceptions.conflict("USER_NOT_ACTIVE", "账号已停用");
        }
        return user;
    }

    private UserAccountRecord requireUser(String id) {
        UserAccountRecord user = accounts.findById(id);
        if (user == null) {
            throw PlatformExceptions.notFound("USER_NOT_FOUND", "账号不存在");
        }
        if (IdentityActivationStatus.DELETED.matches(user.getStatus())) {
            throw PlatformExceptions.notFound("USER_NOT_FOUND", "账号不存在");
        }
        return user;
    }

    private DepartmentRecord requireDepartment(String id) {
        DepartmentRecord department = persistence.findDepartmentById(id);
        if (department == null) {
            throw PlatformExceptions.notFound("DEPARTMENT_NOT_FOUND", "部门不存在");
        }
        return department;
    }

    private String requireRoleId(String roleCode) {
        String id = rolePersistenceService.findActiveRoleId(roleCode);
        if (id == null) {
            throw new IllegalStateException("Required role is not active: " + roleCode);
        }
        return id;
    }

    /** 一次解析账号初始化所需的启用角色，防止逐角色查询放大数据库访问。 */
    private Set<String> requireActiveRoleIds(Set<String> roleCodes) {
        List<RoleEntity> roles = rolePersistenceService.findActiveRolesByCodes(roleCodes);
        if (roles == null) {
            throw new IllegalStateException("角色批量查询返回 null");
        }
        Map<String, String> roleIdsByCode = new LinkedHashMap<>();
        Set<String> uniqueRoleIds = new LinkedHashSet<>();
        for (RoleEntity role : roles) {
            if (role == null
                    || CommonUtils.stripToNull(role.getCode()) == null
                    || CommonUtils.stripToNull(role.getId()) == null
                    || !roleCodes.contains(role.getCode())
                    || roleIdsByCode.putIfAbsent(role.getCode(), role.getId()) != null
                    || !uniqueRoleIds.add(role.getId())) {
                throw new IllegalStateException("角色批量查询返回非法结果");
            }
        }
        for (String roleCode : roleCodes) {
            if (!roleIdsByCode.containsKey(roleCode)) {
                throw new IllegalStateException("Required role is not active: " + roleCode);
            }
        }
        return Set.copyOf(uniqueRoleIds);
    }

    private Set<String> normalizeAssignableRoles(Set<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        if (roles.size() > MAX_ASSIGNABLE_ROLES_PER_USER) {
            throw PlatformExceptions.badRequest("ROLE_LIMIT", "普通角色数量超过限制");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String role : roles) {
            String normalized = normalizeRole(role);
            if (!IdentityRoleCode.isAssignableCode(normalized)) {
                throw PlatformExceptions.badRequest("ROLE_NOT_ASSIGNABLE", "只能在普通业务角色中分配账号");
            }
            result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private static String requireActivationStatus(String status, String message) {
        requireText(status, message);
        try {
            return IdentityActivationStatus.fromCode(CommonUtils.upperOrEmpty(status)).getCode();
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.badRequest("ENUM_VALUE_INVALID", message);
        }
    }

    private static HandoverSelectionMode requireSelectionMode(String selectionMode) {
        requireText(selectionMode, "交接选择模式无效");
        try {
            return HandoverSelectionMode.fromCode(selectionMode.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.badRequest("ENUM_VALUE_INVALID", "交接选择模式无效");
        }
    }

    /** 与角色修改处于同一用例事务；先推进版本再清理已持久化的会话。 */
    private void invalidateRoleSessions(UserAccountRecord user, CurrentActor actor, int changed) {
        if (changed > 0) {
            requireOne(
                    accounts.advanceSessionVersion(user.getId(), actor.getUserId(), clock.instant()),
                    "USER_NOT_ACTIVE",
                    "账号不存在或已停用");
        }
        persistence.deleteSessionsByPrincipalName(user.getWorkEmail());
    }

    private CurrentActor requireSuperAdmin() {
        CurrentActor actor = requireActor();
        if (!actor.isSuperAdmin()) {
            throw new PlatformException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED", "需要超级管理员权限", false);
        }
        return actor;
    }

    private CurrentActor requireActor() {
        return actorProvider
                .currentActor()
                .orElseThrow(
                        () ->
                                new PlatformException(
                                        HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "登录会话已失效", true));
    }

    private void audit(
            String eventType,
            String actorId,
            String targetType,
            String targetId,
            String reason,
            String afterSnapshot,
            String beforeSnapshot,
            Instant now) {
        identityAuditEventPersistenceService.insertAudit(
                ids.newId(),
                eventType,
                actorId,
                targetType,
                targetId,
                reason,
                beforeSnapshot,
                afterSnapshot,
                MDC.get("traceId"),
                now);
    }

}
