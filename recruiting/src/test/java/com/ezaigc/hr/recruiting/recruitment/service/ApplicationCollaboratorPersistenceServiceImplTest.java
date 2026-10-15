package com.ezaigc.hr.recruiting.recruitment.service;

import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_COLLABORATORS_PER_APPLICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import com.ezaigc.hr.recruiting.recruitment.service.impl.ApplicationCollaboratorPersistenceServiceImpl;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationCollaboratorPersistenceServiceImplTest {

  private static final String APPLICATION_ID = "P".repeat(26);
  private static final String ACTOR_ID = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void mapsAllCollaboratorsIntoOneBoundedJdbcBatch() {
    ApplicationCollaboratorPersistenceServiceImpl service =
        spy(new ApplicationCollaboratorPersistenceServiceImpl());
    List<String> userIds = List.of("U".repeat(26), "V".repeat(26));
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_COLLABORATORS_PER_APPLICATION));

    assertThat(service.insertCollaborators(APPLICATION_ID, userIds, ACTOR_ID, NOW)).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<ApplicationCollaborator>> batch =
        ArgumentCaptor.forClass(Collection.class);
    verify(service).saveBatch(batch.capture(), eq(MAX_COLLABORATORS_PER_APPLICATION));
    assertThat(batch.getValue())
        .hasSize(2)
        .allSatisfy(
            collaborator -> {
              assertThat(collaborator.getApplicationId()).isEqualTo(APPLICATION_ID);
              assertThat(collaborator.getUserId()).isIn(userIds);
              assertThat(collaborator.getAddedByUserId()).isEqualTo(ACTOR_ID);
              assertThat(collaborator.getCreateTime()).isEqualTo(NOW);
              assertThat(collaborator.getVersion()).isZero();
            });
  }

  @Test
  void acceptsEmptyAndRejectsOversizedCollaboratorBatches() {
    ApplicationCollaboratorPersistenceServiceImpl service =
        new ApplicationCollaboratorPersistenceServiceImpl();
    List<String> oversized =
        IntStream.rangeClosed(0, MAX_COLLABORATORS_PER_APPLICATION)
            .mapToObj(Integer::toString)
            .toList();

    assertThat(service.insertCollaborators(APPLICATION_ID, List.of(), ACTOR_ID, NOW)).isTrue();
    assertThatThrownBy(
            () -> service.insertCollaborators(APPLICATION_ID, oversized, ACTOR_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("application collaborator batch is invalid");
  }
}
