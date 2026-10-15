package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeSourceApplicationManagerTest {
  private static final String CANDIDATE = "C".repeat(26);
  private static final String VERSION = "V".repeat(26);
  private final TalentIntakeApplicationManager talent = mock(TalentIntakeApplicationManager.class);
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final SourceChannelPersistenceService channels = mock(SourceChannelPersistenceService.class);
  private final ResumeSourceApplicationManager manager = new ResumeSourceApplicationManager();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "talent", talent);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "channels", channels);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
  }

  @Test
  void readsExactHistoricalSourceEvenWhenChannelIsDisabled() {
    var version = new ResumeVersion();
    version.setIntakeId("I".repeat(26));
    var intake = new ResumeIntake();
    intake.setId(version.getIntakeId());
    intake.setSourceChannelId("S".repeat(26));
    var channel = new SourceChannel();
    channel.setName("校园招聘");
    channel.setStatus("DISABLED");
    when(intakes.findCandidateResumeVersion(CANDIDATE, VERSION)).thenReturn(version);
    when(intakes.findIntake(version.getIntakeId())).thenReturn(intake);
    when(channels.findChannel(intake.getSourceChannelId())).thenReturn(channel);
    assertThat(manager.source(CANDIDATE, VERSION).getSourceChannelName()).isEqualTo("校园招聘");
    verify(talent).candidateResume(CANDIDATE, VERSION);
  }

  @Test
  void refusesVersionFromAnotherCandidate() {
    assertThatThrownBy(() -> manager.source(CANDIDATE, VERSION)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(channels);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void preservesAllSourceFieldsWithPresentOrMissingChannel(boolean channelPresent) {
    var version = new ResumeVersion();
    version.setIntakeId("I".repeat(26));
    var intake = new ResumeIntake();
    intake.setId(version.getIntakeId());
    intake.setSourceChannelId("S".repeat(26));
    intake.setSourceRecruiterUserId("R".repeat(26));
    intake.setCreateTime(Instant.parse("2026-01-02T03:04:05Z"));
    var channel = new SourceChannel();
    channel.setName("历史来源");
    channel.setChannelType("MANUAL");
    channel.setStatus("DISABLED");
    when(intakes.findCandidateResumeVersion(CANDIDATE, VERSION)).thenReturn(version);
    when(intakes.findIntake(version.getIntakeId())).thenReturn(intake);
    when(channels.findChannel(intake.getSourceChannelId())).thenReturn(channelPresent ? channel : null);

    var source = manager.source(CANDIDATE, VERSION);

    assertThat(source.getIntakeId()).isEqualTo(intake.getId());
    assertThat(source.getSourceChannelId()).isEqualTo(intake.getSourceChannelId());
    assertThat(source.getSourceRecruiterUserId()).isEqualTo(intake.getSourceRecruiterUserId());
    assertThat(source.getReceivedAt()).isEqualTo(intake.getCreateTime());
    assertThat(source.getSourceChannelName()).isEqualTo(channelPresent ? "历史来源" : null);
    assertThat(source.getChannelType()).isEqualTo(channelPresent ? "MANUAL" : null);
    verify(talent).candidateResume(CANDIDATE, VERSION);
  }

  @Test
  void requiresCandidateReadPermissionBeforeLookingUpSource() {
    when(talent.candidateResume(CANDIDATE, VERSION))
        .thenThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权限"));
    assertThatThrownBy(() -> manager.source(CANDIDATE, VERSION)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(intakes, channels);
  }
}
