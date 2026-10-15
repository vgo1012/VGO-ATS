package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.domain.dto.UpdateSourceChannelDTO;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

class SourceChannelScopeTest {
    @Test
    void channelsAreCompanyWideButOnlySuperAdminCanMaintainThem() {
        var auth = mock(AuthorizationService.class);
        var persistence = mock(SourceChannelPersistenceService.class);
        var actor = new CurrentActorSnapshot("hr-a", "招聘专员", "hr-a@example.invalid", "dept",
                Set.of(), Set.of("report"), Set.of(), Set.of("RECRUITER"), Set.of("talent.read", "talent.manage"), false, false);
        when(auth.currentActor()).thenReturn(actor);
        when(persistence.findChannels(any(), any(), any(), anyInt())).thenReturn(List.of());
        var manager = new TalentIntakeApplicationManager();
        ReflectionTestUtils.setField(manager, "authorization", auth);
        ReflectionTestUtils.setField(manager, "sourceChannelPersistenceService", persistence);
        ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
        ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
        ReflectionTestUtils.setField(manager, "assembler", mock(TalentIntakeAssembler.class));
        ReflectionTestUtils.setField(manager, "talentProcessEventPersistenceService",
            mock(TalentProcessEventPersistenceService.class));
        var ids = mock(PlatformIdGenerator.class);
        when(ids.newId()).thenReturn("audit-event");
        ReflectionTestUtils.setField(manager, "ids", ids);
        manager.channels(null, null, 20);
        verify(persistence).findChannels(isNull(), isNull(), isNull(), eq(21));
        var channel = new SourceChannel(); channel.setId("channel"); channel.setCreateId("hr-b");
        when(persistence.findChannel("channel")).thenReturn(channel);
        assertThat(manager.channel("channel")).isSameAs(channel);
        assertThatThrownBy(() -> manager.updateChannel("channel", new UpdateSourceChannelDTO("名称", "MANUAL", 0)))
                .isInstanceOfSatisfying(PlatformException.class,
                    error -> assertThat(error.code()).isEqualTo("SUPER_ADMIN_REQUIRED"));
        actor.setSuperAdmin(true);
        when(persistence.updateChannel(any(), any(), any(), any(Long.class), any(), any())).thenReturn(1);
        manager.updateChannel("channel", new UpdateSourceChannelDTO("名称", "MANUAL", 0));
    }


    @Test
    void deletesOnlyUnreferencedChannelsAndWritesAuditEvent() {
        var auth = mock(AuthorizationService.class);
        var persistence = mock(SourceChannelPersistenceService.class);
        var events = mock(TalentProcessEventPersistenceService.class);
        var actor = new CurrentActorSnapshot("admin", "管理员", "admin@example.invalid", "dept",
                Set.of(), Set.of(), Set.of(), Set.of("SUPER_ADMIN"), Set.of("talent.read"), true, true);
        when(auth.currentActor()).thenReturn(actor);
        var channel = new SourceChannel(); channel.setId("channel"); channel.setVersion(2);
        when(persistence.findChannel("channel")).thenReturn(channel);
        when(persistence.deleteChannel("channel", 2)).thenReturn(1);
        var manager = manager(auth, persistence, events);

        manager.deleteChannel("channel", new TalentVersionReasonDTO(2, "清理误建渠道"));

        verify(persistence).deleteChannel("channel", 2);
        verify(events).insertEvent(eq("audit-event"), eq("SOURCE_CHANNEL_DELETED"), eq("admin"),
                eq("SOURCE_CHANNEL"), eq("channel"), eq("清理误建渠道"), any());
    }

    @Test
    void mapsReferencedChannelDeleteToStableConflict() {
        var auth = mock(AuthorizationService.class);
        var persistence = mock(SourceChannelPersistenceService.class);
        var actor = new CurrentActorSnapshot("admin", "管理员", "admin@example.invalid", "dept",
                Set.of(), Set.of(), Set.of(), Set.of("SUPER_ADMIN"), Set.of("talent.read"), true, true);
        when(auth.currentActor()).thenReturn(actor);
        var channel = new SourceChannel(); channel.setId("channel"); channel.setVersion(2);
        when(persistence.findChannel("channel")).thenReturn(channel);
        when(persistence.deleteChannel("channel", 2))
                .thenThrow(new DataIntegrityViolationException("foreign key"));

        var events = mock(TalentProcessEventPersistenceService.class);
        assertThatThrownBy(() -> manager(auth, persistence, events)
                .deleteChannel("channel", new TalentVersionReasonDTO(2, "清理误建渠道")))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_CHANNEL_IN_USE"));
        verify(events, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsStaleChannelDeleteVersionWithoutWritingAudit() {
        var auth = mock(AuthorizationService.class);
        var persistence = mock(SourceChannelPersistenceService.class);
        var events = mock(TalentProcessEventPersistenceService.class);
        var actor = new CurrentActorSnapshot("admin", "管理员", "admin@example.invalid", "dept",
                Set.of(), Set.of(), Set.of(), Set.of("SUPER_ADMIN"), Set.of("talent.read"), true, true);
        when(auth.currentActor()).thenReturn(actor);
        var channel = new SourceChannel(); channel.setId("channel"); channel.setVersion(3);
        when(persistence.findChannel("channel")).thenReturn(channel);
        when(persistence.deleteChannel("channel", 2)).thenReturn(0);

        assertThatThrownBy(() -> manager(auth, persistence, events)
                .deleteChannel("channel", new TalentVersionReasonDTO(2, "清理误建渠道")))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
        verify(events, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsChannelDeleteByNonSuperAdmin() {
        var auth = mock(AuthorizationService.class);
        var persistence = mock(SourceChannelPersistenceService.class);
        var actor = new CurrentActorSnapshot("hr", "招聘专员", "hr@example.invalid", "dept",
                Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("talent.read"), false, false);
        when(auth.currentActor()).thenReturn(actor);

        assertThatThrownBy(() -> manager(auth, persistence, mock(TalentProcessEventPersistenceService.class))
                .deleteChannel("channel", new TalentVersionReasonDTO(0, "清理误建渠道")))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("SUPER_ADMIN_REQUIRED"));
        verify(persistence, never()).deleteChannel(any(), any(Long.class));
    }

    private static TalentIntakeApplicationManager manager(
            AuthorizationService auth,
            SourceChannelPersistenceService persistence,
            TalentProcessEventPersistenceService events) {
        var manager = new TalentIntakeApplicationManager();
        ReflectionTestUtils.setField(manager, "authorization", auth);
        ReflectionTestUtils.setField(manager, "sourceChannelPersistenceService", persistence);
        ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
        ReflectionTestUtils.setField(manager, "talentProcessEventPersistenceService", events);
        var ids = mock(PlatformIdGenerator.class);
        when(ids.newId()).thenReturn("audit-event");
        ReflectionTestUtils.setField(manager, "ids", ids);
        return manager;
    }
}
