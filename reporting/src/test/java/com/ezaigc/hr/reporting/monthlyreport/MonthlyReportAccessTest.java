package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MonthlyReportAccessTest {

  @Test
  void permitsSuperAdminAndHrDirectorWithTheDedicatedPermission() {
    assertThat(MonthlyReportAccess.allowed(actor(Set.of("RECRUITER"), Set.of(), true))).isTrue();
    assertThat(MonthlyReportAccess.allowed(
        actor(Set.of("HR_DIRECTOR"), Set.of(MonthlyReportConstants.PERMISSION), false))).isTrue();
  }

  @Test
  void rejectsOrdinaryHrEvenWhenTheDedicatedPermissionWasMisgranted() {
    CurrentActorSnapshot ordinaryHr =
        actor(Set.of("RECRUITER"), Set.of(MonthlyReportConstants.PERMISSION), false);
    AuthorizationService authorization = mock(AuthorizationService.class);
    when(authorization.currentActor()).thenReturn(ordinaryHr);
    MonthlyReportAccess access = access(authorization);

    assertThat(MonthlyReportAccess.allowed(ordinaryHr)).isFalse();
    assertThatThrownBy(access::require)
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code())
        .isEqualTo("MONTHLY_REPORT_FORBIDDEN");
  }

  private static MonthlyReportAccess access(AuthorizationService authorization) {
    MonthlyReportAccess access = new MonthlyReportAccess();
    ReflectionTestUtils.setField(access, "authorization", authorization);
    return access;
  }

  private static CurrentActorSnapshot actor(
      Set<String> roles, Set<String> permissions, boolean superAdmin) {
    return new CurrentActorSnapshot(
        "U".repeat(26), "月报用户", "monthly@example.test", "D".repeat(26), Set.of(), Set.of(),
        Set.of(), roles, permissions, roles.contains("HR_DIRECTOR"), superAdmin);
  }
}
