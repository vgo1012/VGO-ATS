package com.ezaigc.hr.integration.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.integration.mail.application.manager.MailResumeApplicationManager;
import com.ezaigc.hr.integration.mail.domain.MailResumeLinkRequest;
import com.ezaigc.hr.integration.mail.service.MailResumeStore;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MailResumeAuthorizationTest {
  private final MailResumeApplicationManager manager = new MailResumeApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final MailResumeStore store = mock(MailResumeStore.class);
  private final MailJdRoutingService routing = mock(MailJdRoutingService.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();

  MailResumeAuthorizationTest() {
    actor.setUserId("reviewer");
    actor.setPermissions(Set.of("talent.manage"));
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "mapper", store);
    ReflectionTestUtils.setField(manager, "routing", routing);
    ReflectionTestUtils.setField(manager, "cursors", mock(CursorCodec.class));
  }

  @Test
  void rejectsOrdinaryTalentManagersBeforeReadingOrWritingMailData() {
    for (Runnable operation : List.<Runnable>of(
        () -> manager.reviews(null, null, null, null, null, null, 20),
        () -> manager.countReviews(null, null, null, null, null),
        () -> manager.jdOptions(null),
        () -> manager.link("mail", new MailResumeLinkRequest()),
        () -> manager.run("run"))) {
      assertThatThrownBy(operation::run).isInstanceOfSatisfying(PlatformException.class, error -> {
        assertThat(error.status().value()).isEqualTo(403);
        assertThat(error.code()).isEqualTo("SUPER_ADMIN_REQUIRED");
      });
    }
    verifyNoInteractions(store, routing);
  }

  @Test
  void allowsSuperAdminQueriesAndPreservesLinkValidation() {
    actor.setSuperAdmin(true);
    when(routing.activeTargets()).thenReturn(List.of());
    when(store.countReviews("REVIEW_REQUIRED", "DISPATCH_FAILED", null, null, null, null, null))
        .thenReturn(6L);
    assertThat(manager.reviews(null, null, null, null, null, null, 20).getItems()).isEmpty();
    assertThat(manager.countReviews(null, null, null, null, null).getCount()).isEqualTo(6);
    assertThat(manager.jdOptions(null)).isEmpty();
    assertThatThrownBy(() -> manager.link("mail", new MailResumeLinkRequest()))
        .isInstanceOfSatisfying(PlatformException.class, error ->
            assertThat(error.code()).isEqualTo("MAIL_REVIEW_REASON_REQUIRED"));
  }
}
