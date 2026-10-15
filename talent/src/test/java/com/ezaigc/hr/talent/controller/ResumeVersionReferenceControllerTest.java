package com.ezaigc.hr.talent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.application.manager.ResumeVersionReferenceApplicationManager;
import com.ezaigc.hr.talent.domain.response.ResumeVersionReferenceResponse;
import jakarta.validation.Validation;
import java.util.Collections;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class ResumeVersionReferenceControllerTest {
  @Test
  void serializesOnlyTheThreeReferenceFields() {
    String id = "V" + "0".repeat(25);
    var json = JsonMapper.builder().build().valueToTree(new ResumeVersionReferenceResponse(id, 1, "历史简历.pdf"));
    assertThat(json.size()).isEqualTo(3);
    assertThat(json.get("id").asString()).isEqualTo(id);
    assertThat(json.get("versionNo").asInt()).isEqualTo(1);
    assertThat(json.get("originalFileName").asString()).isEqualTo("历史简历.pdf");
  }
  @Test
  void forbidsCachingAndDelegatesTheRequestedHistoricalVersions() {
    var manager = mock(ResumeVersionReferenceApplicationManager.class);
    var ids = List.of("version-old000000000000000");
    when(manager.references(ids)).thenReturn(List.of());
    var controller = new ResumeVersionReferenceController();
    ReflectionTestUtils.setField(controller,"manager",manager);
    var response = controller.references(ids);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(manager).references(ids);
  }

  @Test
  void boundsAndValidatesEveryIdentifier() throws Exception {
    var controller = new ResumeVersionReferenceController();
    var method = ResumeVersionReferenceController.class.getMethod("references",List.class);
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      assertThat(validator.validateParameters(controller,method,new Object[]{Collections.nCopies(101,"version0000000000000000000")})).isNotEmpty();
      assertThat(validator.validateParameters(controller,method,new Object[]{List.of(" ")})).isNotEmpty();
      assertThat(validator.validateParameters(controller,method,new Object[]{List.of("version-old000000000000000")})).isEmpty();
    }
  }
}
