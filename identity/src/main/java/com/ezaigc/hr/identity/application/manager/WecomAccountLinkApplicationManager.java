package com.ezaigc.hr.identity.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.WecomAccountLinkEntity;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentEntity;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentLinkEntity;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentLinkPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WecomAccountLinkApplicationManager {
    @Resource private AuthorizationService authorization;
    @Resource private WecomSyncApplicationManager sync;
    @Resource private WecomMemberPersistenceService members;
    @Resource private WecomDepartmentPersistenceService departments;
    @Resource private WecomAccountLinkPersistenceService links;
    @Resource private WecomDepartmentLinkPersistenceService departmentLinks;
    @Resource private IdentityAdministrationPersistenceService identityAdministration;
    @Resource private IdentityAuditEventPersistenceService auditEvents;
    @Resource private PlatformIdGenerator ids;
    @Resource private Clock clock;

    @Transactional
    public void link(String memberId, String userAccountId, String reason) {
        CurrentActorSnapshot actor = requireAdmin();
        WecomMemberEntity member = requireCurrentMember(memberId);
        AdminUserRecord user = identityAdministration.findUserById(userAccountId);
        if (user == null || !IdentityActivationStatus.ACTIVE.getCode().equals(user.getStatus())) {
            throw PlatformExceptions.badRequest("ACTIVE_USER_REQUIRED", "只能关联启用中的本地账号");
        }
        if (reason == null || reason.isBlank()) {
            throw PlatformExceptions.badRequest("REASON_REQUIRED", "关联原因不能为空");
        }
        WecomAccountLinkEntity link = new WecomAccountLinkEntity();
        link.setId(ids.newId());
        link.setWecomMemberId(member.getId());
        link.setUserAccountId(userAccountId);
        link.setLinkedByUserId(actor.getUserId());
        link.setVersion(0);
        link.setCreateId(actor.getUserId());
        link.setUpdateId(actor.getUserId());
        Instant now = clock.instant();
        link.setCreateTime(now);
        link.setUpdateTime(now);
        try {
            links.save(link);
        } catch (DuplicateKeyException exception) {
            throw PlatformExceptions.conflict("WECOM_ACCOUNT_ALREADY_LINKED", "来源成员或本地账号已被关联");
        }
        auditEvents.insertAudit(ids.newId(), "WECOM_ACCOUNT_LINKED", actor.getUserId(),
                "WECOM_MEMBER", memberId, reason.strip(), null,
                "{\"userAccountId\":\"" + userAccountId + "\"}", MDC.get("traceId"), now);
    }

    @Transactional
    public void unlink(String memberId, String reason) {
        CurrentActorSnapshot actor = requireAdmin();
        requireCurrentMember(memberId);
        if (reason == null || reason.isBlank()) {
            throw PlatformExceptions.badRequest("REASON_REQUIRED", "解除原因不能为空");
        }
        WecomAccountLinkEntity link = links.getOne(Wrappers.<WecomAccountLinkEntity>lambdaQuery()
                .eq(WecomAccountLinkEntity::getWecomMemberId, memberId));
        if (link == null) {
            throw PlatformExceptions.notFound("WECOM_ACCOUNT_LINK_NOT_FOUND", "该来源成员尚未关联本地账号");
        }
        links.removeById(link.getId());
        Instant now = clock.instant();
        auditEvents.insertAudit(ids.newId(), "WECOM_ACCOUNT_UNLINKED", actor.getUserId(),
                "WECOM_MEMBER", memberId, reason.strip(),
                "{\"userAccountId\":\"" + link.getUserAccountId() + "\"}", null,
                MDC.get("traceId"), now);
    }

    @Transactional
    public void linkDepartment(String sourceDepartmentId, String localDepartmentId, String reason) {
        CurrentActorSnapshot actor = requireAdmin();
        WecomDepartmentEntity source = requireCurrentDepartment(sourceDepartmentId);
        DepartmentRecord local = identityAdministration.findDepartmentById(localDepartmentId);
        if (local == null || !IdentityActivationStatus.ACTIVE.getCode().equals(local.getStatus())) {
            throw PlatformExceptions.badRequest("ACTIVE_DEPARTMENT_REQUIRED", "只能关联启用中的本地部门");
        }
        if (reason == null || reason.isBlank()) {
            throw PlatformExceptions.badRequest("REASON_REQUIRED", "关联原因不能为空");
        }
        WecomDepartmentLinkEntity link = new WecomDepartmentLinkEntity();
        link.setId(ids.newId());
        link.setWecomDepartmentId(source.getId());
        link.setLocalDepartmentId(localDepartmentId);
        link.setLinkedByUserId(actor.getUserId());
        link.setVersion(0);
        link.setCreateId(actor.getUserId());
        link.setUpdateId(actor.getUserId());
        Instant now = clock.instant();
        link.setCreateTime(now);
        link.setUpdateTime(now);
        try {
            departmentLinks.save(link);
        } catch (DuplicateKeyException exception) {
            throw PlatformExceptions.conflict("WECOM_DEPARTMENT_ALREADY_LINKED", "来源部门已被关联");
        }
        auditEvents.insertAudit(ids.newId(), "WECOM_DEPARTMENT_LINKED", actor.getUserId(),
                "WECOM_DEPARTMENT", sourceDepartmentId, reason.strip(), null,
                "{\"localDepartmentId\":\"" + localDepartmentId + "\"}", MDC.get("traceId"), now);
    }

    @Transactional
    public void unlinkDepartment(String sourceDepartmentId, String reason) {
        CurrentActorSnapshot actor = requireAdmin();
        requireCurrentDepartment(sourceDepartmentId);
        if (reason == null || reason.isBlank()) {
            throw PlatformExceptions.badRequest("REASON_REQUIRED", "解除原因不能为空");
        }
        WecomDepartmentLinkEntity link = departmentLinks.getOne(
                Wrappers.<WecomDepartmentLinkEntity>lambdaQuery()
                        .eq(WecomDepartmentLinkEntity::getWecomDepartmentId, sourceDepartmentId));
        if (link == null) {
            throw PlatformExceptions.notFound("WECOM_DEPARTMENT_LINK_NOT_FOUND", "该来源部门尚未关联本地部门");
        }
        departmentLinks.removeById(link.getId());
        Instant now = clock.instant();
        auditEvents.insertAudit(ids.newId(), "WECOM_DEPARTMENT_UNLINKED", actor.getUserId(),
                "WECOM_DEPARTMENT", sourceDepartmentId, reason.strip(),
                "{\"localDepartmentId\":\"" + link.getLocalDepartmentId() + "\"}", null,
                MDC.get("traceId"), now);
    }

    private CurrentActorSnapshot requireAdmin() {
        CurrentActorSnapshot actor = authorization.currentActor();
        if (!actor.isSuperAdmin()) {
            throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
        }
        return actor;
    }

    private WecomMemberEntity requireCurrentMember(String memberId) {
        WecomMemberEntity member = members.getById(memberId);
        if (member == null || !member.getCorpId().equals(sync.storedCorp())) {
            throw PlatformExceptions.notFound("WECOM_MEMBER_NOT_FOUND", "未找到当前企业的来源成员");
        }
        return member;
    }

    private WecomDepartmentEntity requireCurrentDepartment(String departmentId) {
        WecomDepartmentEntity department = departments.getById(departmentId);
        if (department == null || !department.getCorpId().equals(sync.storedCorp())) {
            throw PlatformExceptions.notFound("WECOM_DEPARTMENT_NOT_FOUND", "未找到当前企业的来源部门");
        }
        return department;
    }
}
