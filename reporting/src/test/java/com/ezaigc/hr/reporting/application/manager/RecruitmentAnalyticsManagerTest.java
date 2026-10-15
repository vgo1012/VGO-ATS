package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsRowDTO;
import com.ezaigc.hr.reporting.service.MetricService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class RecruitmentAnalyticsManagerTest {
  private static final Instant NOW=Instant.parse("2026-09-15T00:00:00Z");
  private final MetricService service=mock(MetricService.class);
  private final AuthorizationService authorization=mock(AuthorizationService.class);
  private final RecruitmentAnalyticsManager manager=new RecruitmentAnalyticsManager();
  private final CurrentActorSnapshot actor=new CurrentActorSnapshot("actor","Actor",null,"own-department",
      Set.of("support"),Set.of(),Set.of("explicit-department"),Set.of(),Set.of("reporting.read-own"),false,false);

  RecruitmentAnalyticsManagerTest() {
    actor.setRoles(Set.of("RECRUITMENT_MANAGER"));
    actor.setManagedUserIds(Set.of("managed"));
    when(authorization.currentActor()).thenReturn(actor);
    when(authorization.recruitmentReportManagedUserIds()).thenReturn(Set.of("managed"));
    when(service.analyticsResumeCount(any())).thenReturn(15L);
    when(service.analyticsActiveBlacklistCount(any())).thenReturn(3L);
    when(service.analyticsSummary(any())).thenReturn(new RecruitmentAnalyticsRowDTO());
    when(service.analyticsDemand(any())).thenReturn(new RecruitmentAnalyticsRowDTO());
    ReflectionTestUtils.setField(manager,"authorization",authorization);
    ReflectionTestUtils.setField(manager,"service",service);
    ReflectionTestUtils.setField(manager,"clock",Clock.fixed(NOW,ZoneOffset.UTC));
  }

  @Test void everySliceUsesIdenticalOwnerScopeAndDepartmentCannotExpandIt() {
    var response=manager.report(NOW.minus(Duration.ofDays(30)),NOW," another-department "," job ","INTERNAL");
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service).analyticsSummary(capture.capture());
    var q=capture.getValue();
    assertThat(q.getDepartmentId()).isEqualTo("another-department");
    assertThat(q.getJdId()).isEqualTo("job");
    assertThat(q.getRecruitmentType()).isEqualTo("INTERNAL");
    assertThat(q.isAllScope()).isFalse();
    assertThat(q.getActorId()).isEqualTo("actor");
    assertThat(q.getManagedUserIds()).containsExactly("managed");
    verify(service).analyticsResumeCount(q);
    verify(service).analyticsActiveBlacklistCount(q);
    verify(service).analyticsDemand(q);
    verify(service).analyticsTrend(q);
    verify(service).analyticsChannels(q);
    verify(service).analyticsPortraits(q);
    verify(service).analyticsDepartments(q);
    assertThat(response.getEfficiency().getAverageTotalHours()).isNull();
    assertThat(response.getEfficiency().getAverageActiveHours()).isNull();
    assertThat(response.getFunnel()).hasSize(7);
    assertThat(response.getRisk().getActiveBlacklists()).isEqualTo(3);
    assertThat(response.getFunnel().getFirst()).satisfies(milestone -> {
      assertThat(milestone.getKey()).isEqualTo("RESUME");
      assertThat(milestone.getLabel()).isEqualTo("全部简历");
      assertThat(milestone.getCount()).isEqualTo(15);
    });
    assertThat(response.getFunnel().get(1)).satisfies(milestone -> {
      assertThat(milestone.getKey()).isEqualTo("APPLICATION");
      assertThat(milestone.getLabel()).isEqualTo("进入流程");
    });
    assertThat(response.getFunnel().get(5)).satisfies(milestone -> {
      assertThat(milestone.getKey()).isEqualTo("OFFER");
      assertThat(milestone.getLabel()).isEqualTo("进入入职流程");
    });
    assertThat(response.getNotes()).anyMatch(note -> note.contains("Offer 以进入入职流程为准"));
  }

  @Test void resumeTotalsRemainSeparateFromProcessOutcomes() {
    var channel=new RecruitmentAnalyticsRowDTO();
    channel.setId("source"); channel.setName("内部推荐"); channel.setResumes(9);
    channel.setApplications(2); channel.setOffers(1);
    when(service.analyticsChannels(any())).thenReturn(List.of(channel));
    var portrait=new RecruitmentAnalyticsRowDTO();
    portrait.setDimension("AGE"); portrait.setName("25–29岁"); portrait.setResumes(9);
    portrait.setPassed(1); portrait.setPending(1);
    when(service.analyticsPortraits(any())).thenReturn(List.of(portrait));

    var response=manager.report(NOW.minusSeconds(3600),NOW,null,null,null);

    assertThat(response.getChannels()).singleElement().satisfies(row->{
      assertThat(row.getResumes()).isEqualTo(9);
      assertThat(row.getApplications()).isEqualTo(2);
      assertThat(row.getOffers()).isEqualTo(1);
    });
    assertThat(response.getPortraits()).singleElement().satisfies(row->{
      assertThat(row.getResumes()).isEqualTo(9);
      assertThat(row.getPassed()).isEqualTo(1);
      assertThat(row.getPending()).isEqualTo(1);
    });
  }

  @Test void permissionFailureStopsBeforeAnyRead() {
    doThrow(new PlatformException(HttpStatus.FORBIDDEN,"DENIED","无权限",false))
        .when(authorization).requirePermission("reporting.read-own");
    assertThatThrownBy(()->manager.report(NOW.minusSeconds(1),NOW,null,null,null)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(service);
  }

  @Test void rejectsInvalidBoundsAndTypeIncludingFractionBeyond366Days() {
    for (Instant from:List.of(NOW,NOW.plusSeconds(1),NOW.minus(Duration.ofDays(366)).minusNanos(1))) {
      assertThatThrownBy(()->manager.report(from,NOW,null,null,null)).isInstanceOf(PlatformException.class);
    }
    assertThatThrownBy(()->manager.report(NOW.minusSeconds(1),NOW,null,null,"ALL")).isInstanceOf(PlatformException.class);
    assertThatThrownBy(()->manager.report(null,NOW,null,null,null)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(service);
  }

  @Test void companyScopeIsExplicitAndExact366DaysIsAllowed() {
    actor.setCompanyScope(true);
    manager.report(NOW.minus(Duration.ofDays(366)),NOW,null,null,null);
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service).analyticsSummary(capture.capture());
    assertThat(capture.getValue().isAllScope()).isTrue();
  }

  @Test void omittedDatesIncludeHistoricalFactsThroughOneCurrentSnapshot() {
    var response=manager.report(null,null,"department","jd","EXTERNAL");
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service).analyticsSummary(capture.capture());
    var query=capture.getValue();
    assertThat(query.getFrom()).isEqualTo(Instant.EPOCH);
    assertThat(query.getTo()).isEqualTo(NOW.plus(1,ChronoUnit.MICROS));
    assertThat(query.getAsOf()).isEqualTo(NOW);
    assertThat(query.getDepartmentId()).isEqualTo("department");
    assertThat(query.getJdId()).isEqualTo("jd");
    assertThat(query.getRecruitmentType()).isEqualTo("EXTERNAL");
    assertThat(response.getFrom()).isEqualTo(Instant.EPOCH);
    assertThat(response.getTo()).isEqualTo(NOW.plus(1,ChronoUnit.MICROS));
    assertThat(response.getNotes()).anyMatch(note->note.contains("累计"));
    verify(service).analyticsResumeCount(query);
    verify(service).analyticsChannels(query);
    verify(service).analyticsPortraits(query);
  }

  @Test void aSingleMissingDateCannotFallBackToCumulativeStatistics() {
    assertThatThrownBy(()->manager.report(null,NOW,null,null,null)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(()->manager.report(NOW,null,null,null,null)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(service);
  }

  @Test void cumulativeStatisticsUseTheReportingHistoryScopeWithoutChangingOperationalScope() {
    actor.setManagedUserIds(Set.of("active"));
    when(authorization.recruitmentReportManagedUserIds()).thenReturn(Set.of("active","disabled","indirect"));
    var snapshotClock=mock(Clock.class);
    when(snapshotClock.instant()).thenReturn(NOW);
    ReflectionTestUtils.setField(manager,"clock",snapshotClock);

    manager.report(null,null,null,null,null);
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service).analyticsSummary(capture.capture());
    assertThat(capture.getValue().getManagedUserIds()).containsExactlyInAnyOrder("active","disabled","indirect");
    assertThat(actor.getManagedUserIds()).containsExactly("active");
    verify(service).analyticsHrWorkload(capture.getValue());
    verify(snapshotClock,times(1)).instant();
  }

  @Test void cumulativeOrdinaryHrStatisticsStayWithinSelfEvenWhenASnapshotContainsSubordinates() {
    actor.setRoles(Set.of("RECRUITER"));
    manager.report(null,null,null,null,null);
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service).analyticsSummary(capture.capture());
    assertThat(capture.getValue().isAllScope()).isFalse();
    assertThat(capture.getValue().getActorId()).isEqualTo("actor");
    assertThat(capture.getValue().getManagedUserIds()).isEmpty();
    verify(authorization,never()).recruitmentReportManagedUserIds();
    verify(service,never()).analyticsHrWorkload(any());
  }

  @Test void cumulativeAdministratorAndDirectorStatisticsRetainCompanyScope() {
    for (String role:List.of("HR_DIRECTOR","SUPER_ADMIN")) {
      actor.setRoles(Set.of(role));
      actor.setSuperAdmin(role.equals("SUPER_ADMIN"));
      manager.report(null,null,null,null,null);
    }
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service,times(2)).analyticsSummary(capture.capture());
    assertThat(capture.getAllValues()).allSatisfy(query->{
      assertThat(query.isAllScope()).isTrue();
      assertThat(query.getManagedUserIds()).isEmpty();
      assertThat(query.getFrom()).isEqualTo(Instant.EPOCH);
    });
    verify(authorization,never()).recruitmentReportManagedUserIds();
  }

  @Test void departmentManagerReceivesWorkloadUsingExactlyTheDashboardScope() {
    actor.setRoles(Set.of("RECRUITER","RECRUITMENT_MANAGER"));
    var row=new RecruitmentAnalyticsRowDTO();
    row.setId("managed"); row.setName("招聘同事"); row.setApplications(8);
    row.setResumes(11); row.setInterview1(4); row.setInterview2(3); row.setInterview3(2); row.setInactive(true);
    row.setInterviews(5); row.setOffers(3); row.setOnboarded(2); row.setRejected(1);
    when(service.analyticsHrWorkload(any())).thenReturn(List.of(row));
    var response=manager.report(NOW.minusSeconds(3600),NOW,"department","jd","EXTERNAL");
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service).analyticsSummary(capture.capture());
    verify(service).analyticsHrWorkload(capture.getValue());
    assertThat(capture.getValue().isAllScope()).isFalse();
    assertThat(capture.getValue().getManagedUserIds()).containsExactly("managed");
    assertThat(capture.getValue().getDepartmentId()).isEqualTo("department");
    assertThat(capture.getValue().getJdId()).isEqualTo("jd");
    assertThat(capture.getValue().getRecruitmentType()).isEqualTo("EXTERNAL");
    assertThat(response.getHrWorkload()).singleElement().satisfies(workload->{
      assertThat(workload.getOwnerUserId()).isEqualTo("managed");
      assertThat(workload.getDisplayName()).isEqualTo("招聘同事");
      assertThat(workload.getApplications()).isEqualTo(8);
      assertThat(workload.getResumes()).isEqualTo(11);
      assertThat(workload.getInterview1()).isEqualTo(4);
      assertThat(workload.getInterview2()).isEqualTo(3);
      assertThat(workload.getInterview3Plus()).isEqualTo(2);
      assertThat(workload.isSelf()).isFalse();
      assertThat(workload.isInactive()).isTrue();
      assertThat(workload.getInterviews()).isEqualTo(5);
      assertThat(workload.getOffers()).isEqualTo(3);
      assertThat(workload.getOnboarded()).isEqualTo(2);
      assertThat(workload.getRejected()).isEqualTo(1);
    });
  }

  @Test void ordinaryRolesDoNotReadOrReceiveHrWorkload() {
    for (String role:List.of("RECRUITER","INTERVIEWER")) {
      actor.setRoles(Set.of(role));
      actor.setCompanyScope(true);
      assertThat(manager.report(NOW.minusSeconds(1),NOW,null,null,null).getHrWorkload()).isNull();
    }
    verify(service,never()).analyticsHrWorkload(any());
  }

  @Test void companyLeadersReceiveTheirOwnZeroDataRow() {
    var row=new RecruitmentAnalyticsRowDTO();
    row.setId("actor"); row.setName("我");
    when(service.analyticsHrWorkload(any())).thenReturn(List.of(row));
    for (String role:List.of("HR_DIRECTOR","SUPER_ADMIN")) {
      actor.setRoles(Set.of(role));
      actor.setCompanyScope(false);
      actor.setSuperAdmin(role.equals("SUPER_ADMIN"));
      assertThat(manager.report(NOW.minusSeconds(1),NOW,null,null,null).getHrWorkload())
          .singleElement().satisfies(workload->{
            assertThat(workload.isSelf()).isTrue();
            assertThat(workload.isInactive()).isFalse();
            assertThat(workload.getApplications()).isZero();
            assertThat(workload.getResumes()).isZero();
          });
    }
    var capture=ArgumentCaptor.forClass(RecruitmentAnalyticsQuery.class);
    verify(service,times(2)).analyticsHrWorkload(capture.capture());
    assertThat(capture.getAllValues()).allSatisfy(query->assertThat(query.isAllScope()).isTrue());
  }

  @Test void managerWithoutMatchingFlowsReceivesAnEmptyVisibleSection() {
    actor.setRoles(Set.of("RECRUITMENT_MANAGER"));
    assertThat(manager.report(NOW.minusSeconds(1),NOW,null,null,null).getHrWorkload()).isEmpty();
  }
}
