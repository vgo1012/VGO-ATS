package com.ezaigc.hr.talent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

class CandidateCountControllerTest {
  @Test
  void exposesIndependentCountWithSameFilters() throws Exception {
    var method = TalentIntakeController.class.getDeclaredMethod(
        "countCandidates", String.class, String.class, boolean.class, String.class);
    assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/candidates/count");
    var controller = new TalentIntakeController();
    var manager = mock(TalentIntakeApplicationManager.class);
    ReflectionTestUtils.setField(controller, "manager", manager);
    var expected = new CursorCount(21, "next", true);
    when(manager.countCandidates("Java", "cursor", true, "PENDING_SCREENING")).thenReturn(expected);
    assertThat(method.invoke(controller, "Java", "cursor", true, "PENDING_SCREENING"))
        .isSameAs(expected);
  }

  @Test
  void countRejectsInvalidStageAndOversizedQueryAtHttpBoundary() throws Exception {
    var method = TalentIntakeController.class.getDeclaredMethod(
        "countCandidates", String.class, String.class, boolean.class, String.class);
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      var controller = new TalentIntakeController();
      assertThat(validator.validateParameters(controller, method,
          new Object[] {null, null, true, "PENDING_SCREENING"})).isEmpty();
      assertThat(validator.validateParameters(controller, method,
          new Object[] {null, null, true, "SHARED"})).isNotEmpty();
      assertThat(validator.validateParameters(controller, method,
          new Object[] {"x".repeat(4096), null, true, null})).isNotEmpty();
    }
  }
}
