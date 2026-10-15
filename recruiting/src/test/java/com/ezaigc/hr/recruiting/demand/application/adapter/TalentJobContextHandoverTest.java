package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

class TalentJobContextHandoverTest {
  private final TalentJobContextAdapter adapter = new TalentJobContextAdapter();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
  private final JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
  private final DemandEntityPersistenceService demands = mock(DemandEntityPersistenceService.class);
  private final DemandCollaboratorPersistenceService collaborators = mock(DemandCollaboratorPersistenceService.class);
  private final JdEntity jd = new JdEntity();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adapter, "authorization", authorization);
    ReflectionTestUtils.setField(adapter, "versions", versions);
    ReflectionTestUtils.setField(adapter, "jds", jds);
    ReflectionTestUtils.setField(adapter, "demandRows", demands);
    ReflectionTestUtils.setField(adapter, "collaborators", collaborators);
    jd.setCode("JD-000001");
    jd.setId("jd"); jd.setDemandId("historical-demand");
    jd.setOwnerUserId("new-owner"); jd.setOwnerDepartmentId("new-department");
    var demand = new DemandEntity(); demand.setId(jd.getDemandId());
    demand.setOwnerUserId("old-owner"); demand.setOwnerDepartmentId("old-department");
    var version = new JdVersionEntity(); version.setId("version"); version.setJdId(jd.getId());
    version.setTitle("交接后的运维岗位"); version.setVersionNo(1);
    when(versions.listByIds(anyCollection())).thenReturn(List.of(version));
    when(jds.listByIds(anyCollection())).thenReturn(List.of(jd));
    when(demands.listByIds(anyCollection())).thenReturn(List.of(demand));
    when(collaborators.findForDemands(anyList())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), eq("demand.read"), anyCollection()))
        .thenAnswer(invocation -> {
          CurrentActorSnapshot actor = invocation.getArgument(0);
          Collection<AuthorizationTarget> targets = invocation.getArgument(2);
          assertThat(targets).allSatisfy(target -> {
            assertThat(target.getOwnerUserId()).isEqualTo("new-owner");
            assertThat(target.getDepartmentId()).isEqualTo("new-department");
          });
          return targets.stream()
              .filter(target -> actor.getUserId().equals(target.getOwnerUserId())
                  || target.getCollaboratorUserIds().contains(actor.getUserId()))
              .map(AuthorizationTarget::getObjectId).collect(Collectors.toSet());
        });
  }

  @Test
  void oldDemandOwnerLosesPrivateContextWhileCurrentJdOwnerCanReadIt() {
    when(authorization.currentActor()).thenReturn(actor("old-owner"));
    assertThat(adapter.visibleVersions(List.of("version"))).isEmpty();

    when(authorization.currentActor()).thenReturn(actor("new-owner"));
    assertThat(adapter.visibleVersions(List.of("version"))).containsOnlyKeys("version");
    assertThat(adapter.visibleVersions(List.of("version")).get("version").getJdCode()).isEqualTo("JD-000001");
  }

  @Test
  void newUseRequiresAnExistingWriteTransactionAndAuthorizesTheLockedJd() throws NoSuchMethodException {
    var queries = mock(DemandQueryService.class);
    ReflectionTestUtils.setField(adapter, "demands", queries);
    var snapshot = new JdVersionSnapshot("version", "jd", null, "INTERNAL", "new-owner", "new-department", Set.of());
    when(queries.requireJdVersionForNewUse("version")).thenReturn(snapshot);

    adapter.requireVersionForNewUse("version");

    verify(queries).requireJdVersionForNewUse("version");
    verify(authorization).requireAccess(eq("demand.read"), any(AuthorizationTarget.class));
    assertThat(TalentJobContextAdapter.class.getMethod("requireVersionForNewUse", String.class)
        .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
  }

  @Test
  void newUseCannotProceedWhenDeletionWonTheJdLock() {
    var queries = mock(DemandQueryService.class);
    ReflectionTestUtils.setField(adapter, "demands", queries);
    when(queries.requireJdVersionForNewUse("version"))
        .thenThrow(PlatformExceptions.conflict("JD_DELETED", "JD 已删除，不能用于新业务"));
    assertThatThrownBy(() -> adapter.requireVersionForNewUse("version"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_DELETED"));
    verifyNoInteractions(jds);
  }

  @Test
  void legacyPublicFlagDoesNotRestoreAuthorizationAfterHandover() {
    jd.setPublicVisible(true);
    when(authorization.currentActor()).thenReturn(actor("other-recruiter"));

    assertThat(adapter.visibleVersions(List.of("version"))).isEmpty();
  }

  @Test
  void acceptsOneThousandVersionsWithoutChangingPrivateAuthorization() {
    var rows = thousandHistoricalVersions();
    var ids = rows.stream().map(JdVersionEntity::getId).toList();
    when(versions.listByIds(ids)).thenReturn(rows);
    when(authorization.currentActor()).thenReturn(actor("new-owner"));

    var result = adapter.visibleVersions(ids);

    assertThat(result.keySet()).containsExactlyElementsOf(ids);
    assertThat(result.get(ids.getFirst()).getVersionNo()).isEqualTo(1000);
    assertThat(result.get(ids.getLast()).getVersionNo()).isEqualTo(1);
    assertThat(result.values()).allSatisfy(context -> {
      assertThat(context.getJdId()).isEqualTo("jd");
      assertThat(context.getJdCode()).isEqualTo("JD-000001");
    });
    verify(versions).listByIds(ids);
  }

  @Test
  void trustedSubsetExposesOnlyThoseVersionsWhenPrivateAuthorizationDeniesTheJob() {
    var rows = thousandHistoricalVersions();
    var ids = rows.stream().map(JdVersionEntity::getId).toList();
    when(versions.listByIds(ids)).thenReturn(rows);
    when(authorization.currentActor()).thenReturn(actor("other-recruiter"));

    assertThat(adapter.visibleVersions(ids)).isEmpty();
    assertThat(adapter.visibleVersions(ids, Set.of(ids.getFirst(), ids.getLast())))
        .containsOnlyKeys(ids.getFirst(), ids.getLast());

    verify(versions, times(2)).listByIds(ids);
  }

  @Test
  void rejectsOneThousandAndOneVersionsBeforePersistenceForBothOverloads() {
    var ids = IntStream.range(0, 1001)
        .mapToObj(index -> String.format(Locale.ROOT, "%026d", index)).toList();

    assertThatThrownBy(() -> adapter.visibleVersions(ids)).isInstanceOf(PlatformException.class)
        .hasMessage("岗位上下文最多1000项");
    assertThatThrownBy(() -> adapter.visibleVersions(ids, Set.copyOf(ids)))
        .isInstanceOf(PlatformException.class).hasMessage("岗位上下文最多1000项");

    verifyNoInteractions(versions, jds, demands, collaborators, authorization);
  }

  @Test
  void emptyRequestsDoNotReadPersistenceOrAuthorizeForEitherOverload() {
    assertThat(adapter.visibleVersions(List.of())).isEmpty();
    assertThat(adapter.visibleVersions(List.of(), Set.of())).isEmpty();
    verifyNoInteractions(versions, jds, demands, collaborators, authorization);
  }

  private static List<JdVersionEntity> thousandHistoricalVersions() {
    return IntStream.range(0, 1000).mapToObj(index -> {
      var version = new JdVersionEntity();
      version.setId(String.format(Locale.ROOT, "%026d", index));
      version.setJdId("jd");
      version.setTitle("历史岗位");
      version.setVersionNo(1000 - index);
      return version;
    }).toList();
  }

  private CurrentActorSnapshot actor(String userId) {
    return new CurrentActorSnapshot(userId, userId, userId + "@example.invalid", "department",
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("demand.read"), false, false);
  }
}
