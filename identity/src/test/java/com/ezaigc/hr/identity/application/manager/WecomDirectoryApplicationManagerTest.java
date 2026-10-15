package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.WecomDepartmentDTO;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.api.WecomMemberDTO;
import com.ezaigc.hr.identity.application.assembler.WecomDirectoryAssembler;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class WecomDirectoryApplicationManagerTest {
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final WecomDirectoryPort directory = mock(WecomDirectoryPort.class);
    private final WecomDirectoryApplicationManager manager = new WecomDirectoryApplicationManager();
    private final CurrentActorSnapshot actor = new CurrentActorSnapshot();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(manager, "authorization", authorization);
        ReflectionTestUtils.setField(manager, "directory", directory);
        ReflectionTestUtils.setField(manager, "assembler", new WecomDirectoryAssembler());
        when(authorization.currentActor()).thenReturn(actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HR", "HR_DIRECTOR", "DEPARTMENT_MANAGER", "INTERVIEWER"})
    void onlySuperAdminCanPreviewOrInspectConfiguration(String role) {
        actor.setRoles(Set.of(role));
        actor.setPermissions(Set.of("identity.admin", "interview.manage"));
        assertThatThrownBy(manager::preview).isInstanceOf(PlatformException.class);
        assertThatThrownBy(manager::status).isInstanceOf(PlatformException.class);
        verifyNoInteractions(directory);
    }

    @Test
    void superAdminReceivesReadOnlyPreviewAndSeparateHttpModels() {
        actor.setSuperAdmin(true);
        var result = new WecomDirectoryResultDTO("PREVIEW_READY", "预览", "corp-test", Instant.EPOCH, 60,
                List.of(new WecomDepartmentDTO(2, 1, "人力", 0, true)),
                List.of(new WecomMemberDTO("member", "测试成员", null, null, 1, List.of(2L))));
        when(directory.preview()).thenReturn(result);
        when(directory.status()).thenReturn(new WecomDirectoryResultDTO("READY", "未连接", "corp-test",
                Instant.EPOCH, 0, List.of(), List.of()));
        var response = manager.preview();
        assertThat(response.getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(response.getDepartments().getFirst().isParentOutsideScope()).isTrue();
        assertThat(response.getMembers().getFirst().getBusinessEmail()).isNull();
        assertThat(response.getMembers().getFirst().getDepartmentIds()).containsExactly(2L);
        assertThat(manager.status().getStatus()).isEqualTo("READY");
    }
}
