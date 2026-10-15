package com.ezaigc.hr.talent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.application.manager.ResumeVersionSourceApplicationManager;
import jakarta.validation.Validation;
import java.util.Collections;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeVersionSourceControllerTest {
  @Test
  void forbidsCachingAndDelegatesRequestedHistoricalVersions() {
    var manager = mock(ResumeVersionSourceApplicationManager.class);
    var ids = List.of("version-old000000000000000");
    when(manager.sources(ids)).thenReturn(List.of());
    var controller = new ResumeVersionSourceController();
    ReflectionTestUtils.setField(controller, "manager", manager);

    var response = controller.sources(ids);

    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(manager).sources(ids);
  }

  @Test
  void boundsAndValidatesEveryIdentifier() throws Exception {
    var controller = new ResumeVersionSourceController();
    var method = ResumeVersionSourceController.class.getMethod("sources", List.class);
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      assertThat(validator.validateParameters(controller, method,
          new Object[] {Collections.nCopies(101, "version0000000000000000000")})).isNotEmpty();
      assertThat(validator.validateParameters(controller, method,
          new Object[] {List.of(" ")})).isNotEmpty();
      assertThat(validator.validateParameters(controller, method,
          new Object[] {List.of("version-old000000000000000")})).isEmpty();
    }
  }
}
