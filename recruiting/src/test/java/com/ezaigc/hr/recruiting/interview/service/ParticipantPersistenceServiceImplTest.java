package com.ezaigc.hr.recruiting.interview.service;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_PARTICIPANTS_PER_INTERVIEW;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_NOTIFICATION_INTERVIEWS_PER_BATCH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.service.impl.ParticipantPersistenceServiceImpl;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ezaigc.hr.recruiting.interview.mapper.ParticipantMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;

class ParticipantPersistenceServiceImplTest {

  private static final String INTERVIEW_ID = "I".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @BeforeAll
  static void initializeMetadata() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "participant-test"), Participant.class);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void notificationCurrentReadIsBoundedAndLocksTheSelectedInterviews() {
    ParticipantMapper mapper = mock(ParticipantMapper.class);
    ParticipantPersistenceServiceImpl service = new ParticipantPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var participant = new Participant(INTERVIEW_ID, "U".repeat(26), "INTERVIEWER", "PENDING");
    when(mapper.selectList(any())).thenReturn(List.of(participant));
    assertThat(service.lockParticipantsByInterviewIds(List.of(INTERVIEW_ID, "J".repeat(26))))
        .containsExactly(participant);
    ArgumentCaptor<LambdaQueryWrapper<Participant>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(query.capture());
    assertThat(query.getValue().getSqlSegment()).contains("interview_id IN", "ORDER BY interview_id ASC,participant_role ASC,user_id ASC")
        .endsWith("LIMIT 5001 FOR UPDATE");
    assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(INTERVIEW_ID, "J".repeat(26));
  }

  @Test
  void emptyCurrentReadDoesNotAcquireAnyLocks() {
    assertThat(new ParticipantPersistenceServiceImpl().lockParticipantsByInterviewIds(List.of())).isEmpty();
  }

  @Test
  void invalidLockingBatchIsRejectedBeforePersistence() {
    ParticipantMapper mapper = mock(ParticipantMapper.class);
    ParticipantPersistenceServiceImpl service = new ParticipantPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    assertThatThrownBy(() -> service.lockParticipantsByInterviewIds(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.lockParticipantsByInterviewIds(Collections.nCopies(MAX_NOTIFICATION_INTERVIEWS_PER_BATCH + 1, INTERVIEW_ID)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.lockParticipantsByInterviewIds(Arrays.asList(INTERVIEW_ID, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.lockParticipantsByInterviewIds(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper);
  }

  @Test
  void oversizedHistoricalParticipantDataFailsInsteadOfSilentlyTruncatingRecipients() {
    ParticipantMapper mapper = mock(ParticipantMapper.class);
    ParticipantPersistenceServiceImpl service = new ParticipantPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.selectList(any())).thenReturn(Collections.nCopies(MAX_PARTICIPANTS_PER_INTERVIEW + 1, new Participant()));
    assertThatThrownBy(() -> service.lockParticipantsByInterviewIds(List.of(INTERVIEW_ID)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("capacity");
  }

  @Test
  void mapsEveryParticipantIntoOneBoundedJdbcBatch() {
    ParticipantPersistenceServiceImpl service = spy(new ParticipantPersistenceServiceImpl());
    List<String> userIds =
        IntStream.rangeClosed(1, MAX_PARTICIPANTS_PER_INTERVIEW)
            .mapToObj(index -> String.format("%026d", index))
            .toList();
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_PARTICIPANTS_PER_INTERVIEW));

    assertThat(service.insertParticipants(INTERVIEW_ID, userIds, "INTERVIEWER", NOW)).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Participant>> batch = ArgumentCaptor.forClass(Collection.class);
    verify(service).saveBatch(batch.capture(), eq(MAX_PARTICIPANTS_PER_INTERVIEW));
    assertThat(batch.getValue())
        .hasSize(MAX_PARTICIPANTS_PER_INTERVIEW)
        .allSatisfy(
            participant -> {
              assertThat(participant.getInterviewId()).isEqualTo(INTERVIEW_ID);
              assertThat(participant.getUserId()).isIn(userIds);
              assertThat(participant.getParticipantRole()).isEqualTo("INTERVIEWER");
              assertThat(participant.getResponseStatus()).isEqualTo("PENDING");
              assertThat(participant.getCreateTime()).isEqualTo(NOW);
            });
  }

  @Test
  void acceptsAnEmptyBatchWithoutOpeningADataSession() {
    assertThat(
            new ParticipantPersistenceServiceImpl()
                .insertParticipants(INTERVIEW_ID, List.of(), "INTERVIEWER", NOW))
        .isTrue();
  }

  @Test
  void rejectsOversizedAndMalformedBatchesBeforePersistence() {
    ParticipantPersistenceServiceImpl service = new ParticipantPersistenceServiceImpl();
    List<String> oversized =
        IntStream.rangeClosed(0, MAX_PARTICIPANTS_PER_INTERVIEW)
            .mapToObj(Integer::toString)
            .toList();

    assertThatThrownBy(
            () -> service.insertParticipants(INTERVIEW_ID, oversized, "INTERVIEWER", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("interview participant batch is invalid");
    assertThatThrownBy(
            () -> service.insertParticipants(INTERVIEW_ID, List.of(" "), "INTERVIEWER", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("interview participant batch is invalid");
  }
}
