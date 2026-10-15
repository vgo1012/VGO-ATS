package com.ezaigc.hr.talent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.application.manager.ResumeVersionFactSummaryApplicationManager;
import jakarta.validation.Validation;
import java.util.Collections;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeVersionFactSummaryControllerTest {
  @Test
  void forbidsCachingAndDelegatesTheRequestedHistoricalVersions() {
    var manager = mock(ResumeVersionFactSummaryApplicationManager.class);
    var ids = List.of("version-old000000000000000");
    when(manager.summaries(ids)).thenReturn(List.of());
    var controller = new ResumeVersionFactSummaryController();
    ReflectionTestUtils.setField(controller,"manager",manager);
    var response = controller.summaries(ids);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(manager).summaries(ids);
  }

  @Test
  void boundsAndValidatesEveryIdentifier() throws Exception {
    var controller = new ResumeVersionFactSummaryController();
    var method = ResumeVersionFactSummaryController.class.getMethod("summaries",List.class);
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      assertThat(validator.validateParameters(controller,method,new Object[]{Collections.nCopies(101,"version0000000000000000000")})).isNotEmpty();
      assertThat(validator.validateParameters(controller,method,new Object[]{List.of(" ")})).isNotEmpty();
      assertThat(validator.validateParameters(controller,method,new Object[]{List.of("version-old000000000000000")})).isEmpty();
    }
  }
}
