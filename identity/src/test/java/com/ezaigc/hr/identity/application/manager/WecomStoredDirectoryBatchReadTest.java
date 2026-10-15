package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.application.assembler.WecomSyncAssembler;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.WecomAccountLinkEntity;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.domain.entity.WecomSyncStateEntity;
import com.ezaigc.hr.identity.domain.response.WecomStoredMemberResponse;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import com.ezaigc.hr.identity.service.WecomSyncStatePersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class WecomStoredDirectoryBatchReadTest {
  @Test
  void readsLinkedAccountsOnceWithoutChangingMissingOrUnlinkedMembers() {
    WecomSyncApplicationManager manager = new WecomSyncApplicationManager();
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot(); actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    WecomDirectoryPort directory = mock(WecomDirectoryPort.class);
    when(directory.status()).thenReturn(new WecomDirectoryResultDTO("AVAILABLE", null, "corp", null, 0, List.of(), List.of()));
    WecomSyncStatePersistenceService states = mock(WecomSyncStatePersistenceService.class);
    when(states.getById("corp")).thenReturn(new WecomSyncStateEntity());
    WecomAccountLinkPersistenceService links = mock(WecomAccountLinkPersistenceService.class);
    when(links.list()).thenReturn(List.of(link("m1", "u1"), link("m2", "u1"), link("m3", "missing")));
    IdentityAdministrationPersistenceService identity = mock(IdentityAdministrationPersistenceService.class);
    AdminUserRecord user = new AdminUserRecord(); user.setId("u1"); user.setDisplayName("已关联用户");
    when(identity.findUserById("u1")).thenReturn(user);
    when(identity.findUsersByIds(any())).thenReturn(List.of(user));
    WecomMemberPersistenceService members = mock(WecomMemberPersistenceService.class);
    when(members.list(ArgumentMatchers.<Wrapper<WecomMemberEntity>>any()))
        .thenReturn(List.of(member("m1"), member("m2"), member("m3"), member("m4")));
    WecomSyncAssembler assembler = new WecomSyncAssembler();
    ReflectionTestUtils.setField(assembler, "json", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "directory", directory);
    ReflectionTestUtils.setField(manager, "states", states);
    ReflectionTestUtils.setField(manager, "accountLinks", links);
    ReflectionTestUtils.setField(manager, "identityAdministration", identity);
    ReflectionTestUtils.setField(manager, "members", members);
    ReflectionTestUtils.setField(manager, "departments", mock(WecomDepartmentPersistenceService.class));
    ReflectionTestUtils.setField(manager, "departmentLinks", mock(WecomDepartmentLinkPersistenceService.class));
    ReflectionTestUtils.setField(manager, "assembler", assembler);

    assertThat(manager.stored().getMembers()).extracting(WecomStoredMemberResponse::getLinkedUserDisplayName)
        .containsExactly("已关联用户", "已关联用户", null, null);
    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass((Class) List.class);
    verify(identity).findUsersByIds(ids.capture());
    assertThat(ids.getValue()).containsExactlyInAnyOrder("u1", "missing");
    verify(identity, never()).findUserById(any());
  }

  private static WecomAccountLinkEntity link(String member, String user) {
    WecomAccountLinkEntity link = new WecomAccountLinkEntity();
    link.setWecomMemberId(member); link.setUserAccountId(user); return link;
  }
  private static WecomMemberEntity member(String id) {
    WecomMemberEntity member = new WecomMemberEntity();
    member.setId(id); member.setUserId(id); member.setDepartmentIdsJson("[]"); return member;
  }
}
