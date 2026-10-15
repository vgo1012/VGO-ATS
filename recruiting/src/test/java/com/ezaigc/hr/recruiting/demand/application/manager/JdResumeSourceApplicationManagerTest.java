package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateJdResumeSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdResumeSourceEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailSourceMessageEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdResumeSourcePersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdSourcePersistenceService;
import com.ezaigc.hr.recruiting.demand.service.MailSourceMessagePersistenceService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.ResumeSubmissionQueryService;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class JdResumeSourceApplicationManagerTest {
  private final JdResumeSourceApplicationManager manager = new JdResumeSourceApplicationManager();
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final MailSourceMessagePersistenceService messages = mock(MailSourceMessagePersistenceService.class);
  private final JdResumeSourcePersistenceService sources = mock(JdResumeSourcePersistenceService.class);
  private final CandidateDirectoryService candidates = mock(CandidateDirectoryService.class);
  private final Map<String, MailSourceMessageEntity> mailRows = new HashMap<>();
  private final Map<String, JdResumeSourceEntity> sourceRows = new HashMap<>();
  private final Map<String, JdSourceEntity> origins = new HashMap<>();
  private final JdVersionSnapshot context = new JdVersionSnapshot();
  private final DemandQueryService queries = mock(DemandQueryService.class);

  @BeforeEach
  void setup() {
    var actor = new CurrentActorSnapshot();
    actor.setUserId("actor"); actor.setWorkEmail("hr@example.invalid");
    when(auth.currentActor()).thenReturn(actor);
    context.setOwnerUserId("actor");
    when(queries.requireJdVersion(anyString())).thenReturn(context);
    when(queries.requireJdVersionForNewUse(anyString())).thenReturn(context);
    var jdSources = mock(JdSourcePersistenceService.class);
    when(jdSources.findSource(anyString(), anyString(), anyString())).thenAnswer(i -> origins.get(i.getArgument(2)));
    when(jdSources.save(any(JdSourceEntity.class))).thenAnswer(i -> {
      JdSourceEntity row = i.getArgument(0); origins.put(row.getSourceKey(), row); return true;
    });
    ReflectionTestUtils.setField(manager, "jdSources", jdSources);
    var versions = mock(JdVersionPersistenceService.class);
    var version = new JdVersionEntity(); version.setRawText("职责：Linux 运维");
    when(versions.findJdVersion(anyString())).thenReturn(version);
    when(candidates.lockActive(anyString())).thenAnswer(i -> new CandidateDirectoryService.CandidateRef(i.getArgument(0),"合成候选人"));
    var resumes = mock(ResumeSubmissionQueryService.class);
    when(resumes.requireSubmissionSnapshot(anyString(),anyString())).thenAnswer(i -> new ResumeSubmissionQueryService.ResumeSubmissionSnapshot(i.getArgument(1),i.getArgument(0),"original.pdf","a".repeat(64)));
    var ids = mock(PlatformIdGenerator.class);
    int[] sequence = {0}; when(ids.newId()).thenAnswer(i -> "id-" + ++sequence[0]);
    doAnswer(i -> { MailSourceMessageEntity r=i.getArgument(0);mailRows.putIfAbsent(r.getMessageHash(),r);return null; }).when(messages).insertIfAbsent(any());
    when(messages.findExisting(anyString(),anyString())).thenAnswer(i -> mailRows.get(i.getArgument(1)));
    doAnswer(i -> { JdResumeSourceEntity r=i.getArgument(0);sourceRows.putIfAbsent(r.getContentSha256(),r);return null; }).when(sources).insertIfAbsent(any());
    when(sources.findExisting(anyString(),anyString())).thenAnswer(i -> sourceRows.get(i.getArgument(1)));
    var audit=mock(DemandAuditEventPersistenceService.class);
    when(audit.insertAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),any())).thenReturn(1);
    for (var entry:Map.of("authorization",auth,"queries",queries,"versions",versions,"candidates",candidates,"resumes",resumes,"messages",messages,"sources",sources,"audit",audit,"ids",ids,"clock",Clock.systemUTC()).entrySet()) {
      ReflectionTestUtils.setField(manager,entry.getKey(),entry.getValue());
    }
  }
  private CreateJdResumeSourceDTO command(String candidate,String subject,String body) {
    return new CreateJdResumeSourceDTO(candidate,"resume","<message@example.invalid>","sender@example.invalid",subject,body);
  }
  private String body() { return "您好\n【JD开始】\n职责：Linux 运维\n【JD结束】\n附件见邮件"; }

  @Test
  void bindsTrustedVersionsAndDeduplicatesRepeatedDelivery() {
    var first=manager.register("jd",command("candidate","subject",body()));
    var replay=manager.register("jd",command("candidate","subject",body()));
    assertThat(replay.getId()).isEqualTo(first.getId());
    assertThat(first.getOriginalFileName()).isEqualTo("original.pdf");
    assertThat(first.getResumeVersionId()).isEqualTo("resume");
    assertThat(mailRows.values().iterator().next().getRecipientEmail()).isEqualTo("hr@example.invalid");
    assertThat(sourceRows).hasSize(1);
    assertThat(origins).hasSize(1);
    assertThat(origins.values().iterator().next().getSourceType()).isEqualTo("MAIL");
  }
  @Test
  void rejectsMessageReuseAndAttachmentIdentityConflict() {
    manager.register("jd",command("candidate","subject",body()));
    assertThatThrownBy(() -> manager.register("jd",command("candidate","changed",body()))).isInstanceOf(PlatformException.class).hasMessageContaining("Message-ID");
    assertThatThrownBy(() -> manager.register("jd",command("other","subject",body()))).isInstanceOf(PlatformException.class).hasMessageContaining("其他候选人");
  }
  @Test
  void rejectsDifferentOrAmbiguousJdBeforeTouchingCandidate() {
    assertThatThrownBy(() -> manager.register("jd",command("candidate","subject",body()+body()))).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.register("jd",command("candidate","subject",body().replace("Linux","Java")))).isInstanceOf(PlatformException.class);
    verify(candidates,never()).lockActive(anyString());
  }
  @Test
  void deniedHrCannotRegisterOrReadMailContents() {
    doThrow(new PlatformException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","禁止访问",false)).when(auth).requireAccess(anyString(),any());
    assertThatThrownBy(() -> manager.register("jd",command("candidate","subject",body()))).isInstanceOf(PlatformException.class);
    var message=new MailSourceMessageEntity();message.setJdVersionId("jd");
    when(messages.getById("mail")).thenReturn(message);
    assertThatThrownBy(() -> manager.mail("mail")).isInstanceOf(PlatformException.class);
    verify(sources,never()).insertIfAbsent(any());
  }

  @Test
  void legacyPublicFlagDoesNotOverrideCurrentJdAuthorization() {
    context.setPublicVisible(true);
    var message = new MailSourceMessageEntity(); message.setJdVersionId("jd"); message.setCreateId("other-hr");
    when(messages.getById("mail")).thenReturn(message);
    assertThat(manager.mail("mail")).isSameAs(message);
  }
}
