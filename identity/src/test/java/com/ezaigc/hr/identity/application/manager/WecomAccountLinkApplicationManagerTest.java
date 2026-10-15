package com.ezaigc.hr.identity.application.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentEntity;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentLinkPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WecomAccountLinkApplicationManagerTest {
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final WecomSyncApplicationManager sync = mock(WecomSyncApplicationManager.class);
    private final WecomMemberPersistenceService members = mock(WecomMemberPersistenceService.class);
    private final WecomAccountLinkPersistenceService links = mock(WecomAccountLinkPersistenceService.class);
    private final WecomDepartmentPersistenceService departments = mock(WecomDepartmentPersistenceService.class);
    private final WecomDepartmentLinkPersistenceService departmentLinks = mock(WecomDepartmentLinkPersistenceService.class);
    private final IdentityAdministrationPersistenceService identity = mock(IdentityAdministrationPersistenceService.class);
    private final IdentityAuditEventPersistenceService audits = mock(IdentityAuditEventPersistenceService.class);
    private final WecomAccountLinkApplicationManager manager = new WecomAccountLinkApplicationManager();

    @BeforeEach
    void setUp() {
        CurrentActorSnapshot actor = new CurrentActorSnapshot();
        actor.setUserId("01H00000000000000000000001");
        actor.setSuperAdmin(true);
        when(authorization.currentActor()).thenReturn(actor);
        when(sync.configuredCorp()).thenReturn("corp-1");
        when(sync.storedCorp()).thenReturn("corp-1");
        AtomicInteger sequence = new AtomicInteger();
        PlatformIdGenerator ids = () -> String.format("01H%023d", sequence.incrementAndGet());
        ReflectionTestUtils.setField(manager, "authorization", authorization);
        ReflectionTestUtils.setField(manager, "sync", sync);
        ReflectionTestUtils.setField(manager, "members", members);
        ReflectionTestUtils.setField(manager, "links", links);
        ReflectionTestUtils.setField(manager, "departments", departments);
        ReflectionTestUtils.setField(manager, "departmentLinks", departmentLinks);
        ReflectionTestUtils.setField(manager, "identityAdministration", identity);
        ReflectionTestUtils.setField(manager, "auditEvents", audits);
        ReflectionTestUtils.setField(manager, "ids", ids);
        ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-17T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void linksCurrentEnterpriseMemberToOneActiveAccountAndAuditsIt() {
        WecomMemberEntity member = new WecomMemberEntity();
        member.setId("01H00000000000000000000002");
        member.setCorpId("corp-1");
        when(members.getById(member.getId())).thenReturn(member);
        AdminUserRecord user = new AdminUserRecord();
        user.setId("01H00000000000000000000003");
        user.setStatus("ACTIVE");
        when(identity.findUserById(user.getId())).thenReturn(user);
        when(links.save(any())).thenReturn(true);

        manager.link(member.getId(), user.getId(), "管理员核对身份");

        verify(links).save(any());
        verify(audits).insertAudit(any(), eq("WECOM_ACCOUNT_LINKED"),
                eq("01H00000000000000000000001"), eq("WECOM_MEMBER"), eq(member.getId()),
                eq("管理员核对身份"), eq(null), eq("{\"userAccountId\":\"01H00000000000000000000003\"}"),
                any(), eq(Instant.parse("2026-09-17T03:00:00Z")));
    }

    @Test
    void linksCurrentSourceDepartmentToActiveLocalDepartmentAndAuditsIt() {
        WecomDepartmentEntity source = new WecomDepartmentEntity();
        source.setId("01H00000000000000000000004");
        source.setCorpId("corp-1");
        when(departments.getById(source.getId())).thenReturn(source);
        DepartmentRecord local = new DepartmentRecord();
        local.setId("01H00000000000000000000005");
        local.setStatus("ACTIVE");
        when(identity.findDepartmentById(local.getId())).thenReturn(local);
        when(departmentLinks.save(any())).thenReturn(true);

        manager.linkDepartment(source.getId(), local.getId(), "管理员核对组织");

        verify(departmentLinks).save(any());
        verify(audits).insertAudit(any(), eq("WECOM_DEPARTMENT_LINKED"),
                eq("01H00000000000000000000001"), eq("WECOM_DEPARTMENT"), eq(source.getId()),
                eq("管理员核对组织"), eq(null), eq("{\"localDepartmentId\":\"01H00000000000000000000005\"}"),
                any(), eq(Instant.parse("2026-09-17T03:00:00Z")));
    }
}
