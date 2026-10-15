package com.ezaigc.hr.talent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.response.DownloadedResumeResponse;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

class TalentIntakeControllerFileTest {
  private static final String ID = "01JBATCH000000000000000000";

  @Test
  void fileEndpointStreamsOriginalWithPrivateDownloadHeaders() throws Exception {
    var controller = new TalentIntakeController();
    var manager = mock(TalentIntakeApplicationManager.class);
    var stream = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ReflectionTestUtils.setField(controller, "manager", manager);
    when(manager.downloadIntakeFile(ID)).thenReturn(
        new DownloadedResumeResponse("原始简历.pdf", "application/pdf", 3, stream));
    var response = controller.intakeFile(ID);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(3);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    var disposition = ContentDisposition.parse(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    assertThat(disposition.getType()).isEqualTo("attachment");
    assertThat(disposition.getFilename()).isEqualTo("原始简历.pdf");
    assertThat(response.getBody().getInputStream()).isSameAs(stream);
    verify(manager).downloadIntakeFile(ID);
  }

  @Test
  void fileHttpContractValidatesTheIntakeIdentifier() throws Exception {
    var method = TalentIntakeController.class.getDeclaredMethod("intakeFile", String.class);
    assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/intakes/{id}/file");
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      assertThat(validator.validateParameters(new TalentIntakeController(), method, new Object[] {ID})).isEmpty();
      assertThat(validator.validateParameters(new TalentIntakeController(), method, new Object[] {"../other"})).isNotEmpty();
    }
  }
}
