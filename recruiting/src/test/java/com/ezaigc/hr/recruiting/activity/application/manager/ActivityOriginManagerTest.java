package com.ezaigc.hr.recruiting.activity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ActivityOriginManagerTest {
  private final ActivityOriginManager manager = new ActivityOriginManager();
  private final ActivityOriginPersistenceService origins = mock(ActivityOriginPersistenceService.class);

  ActivityOriginManagerTest() {
    ReflectionTestUtils.setField(manager, "origins", origins);
  }

  @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

  @Test void legacyAndBackgroundCommandsDoNotInventButtonOrigins() {
    manager.record("a", "COMMUNICATION", "c");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    manager.record("a", "COMMUNICATION", "c");
    verifyNoInteractions(origins);
  }

  @Test void rejectsIncompleteOrInvalidContext() {
    for (String operation : new String[] {null, "not-a-uuid", "1-1-1-1-1"}) {
      assertThatThrownBy(() -> ActivityOriginManager.validateContext("INTERVIEW", operation)).isInstanceOf(PlatformException.class);
    }
    assertThatThrownBy(() -> ActivityOriginManager.validateContext("OTHER", "cbcc7d89-94f3-4a32-a154-af5068769231"))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(origins);
  }

  @Test void persistsExplicitActionAndActorAlongsideSourceFact() {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Recruitment-Action", "INTERVIEW");
    request.addHeader("X-Recruitment-Operation", "cbcc7d89-94f3-4a32-a154-af5068769231");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    var ids = mock(PlatformIdGenerator.class);
    var authorization = mock(AuthorizationService.class);
    var actor = new CurrentActorSnapshot(); actor.setUserId("actor"); actor.setDisplayName("操作人");
    when(authorization.currentActor()).thenReturn(actor);
    when(ids.newId()).thenReturn("I".repeat(26));
    when(origins.save(any())).thenReturn(true);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    manager.record("application", "INTERVIEW_SCHEDULE", "history");
    var captured = ArgumentCaptor.forClass(ActivityOriginEntity.class);
    verify(origins).save(captured.capture());
    assertThat(captured.getValue().getAction()).isEqualTo("INTERVIEW");
    assertThat(captured.getValue().getSourceId()).isEqualTo("history");
    assertThat(captured.getValue().getCreateName()).isEqualTo("操作人");
    assertThat(captured.getValue().getApplicationId()).isEqualTo("application");
  }
}
