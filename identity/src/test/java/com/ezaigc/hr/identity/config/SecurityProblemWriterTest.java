package com.ezaigc.hr.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SecurityProblemWriterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @ParameterizedTest
  @CsvSource({
      "en-US, SESSION_REQUIRED, session-required, 401, true",
      "en-US, ACCESS_DENIED, access-denied, 403, false",
      "en-US, CSRF_TOKEN_INVALID, csrf-token-invalid, 403, true",
      "en-US, PASSWORD_CHANGE_REQUIRED, password-change-required, 403, true",
      "tr-TR, SESSION_REQUIRED, session-required, 401, true",
      "tr-TR, ACCESS_DENIED, access-denied, 403, false",
      "tr-TR, CSRF_TOKEN_INVALID, csrf-token-invalid, 403, true",
      "tr-TR, PASSWORD_CHANGE_REQUIRED, password-change-required, 403, true",
      "az-AZ, SESSION_REQUIRED, session-required, 401, true",
      "az-AZ, ACCESS_DENIED, access-denied, 403, false",
      "az-AZ, CSRF_TOKEN_INVALID, csrf-token-invalid, 403, true",
      "az-AZ, PASSWORD_CHANGE_REQUIRED, password-change-required, 403, true"
  })
  @ResourceLock(Resources.LOCALE)
  void writesStableProblemTypesIndependentlyOfDefaultLocale(
      String languageTag, String code, String typeSuffix, int status, boolean recoverable) throws Exception {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag(languageTag));
      var response = new MockHttpServletResponse();
      SecurityProblemWriter.write(response, status, code, "错误详情");

      JsonNode problem = mapper.readTree(response.getContentAsString());
      assertThat(problem.get("type").asString()).isEqualTo("urn:vgo:problem:" + typeSuffix);
      assertThat(problem.get("code").asString()).isEqualTo(code);
      assertThat(problem.get("title").asString()).isEqualTo(status == 401 ? "Unauthorized" : "Forbidden");
      assertThat(problem.get("status").asInt()).isEqualTo(status);
      assertThat(problem.get("detail").asString()).isEqualTo("错误详情");
      // 本模块的 slf4j-simple 不存储 MDC；真实跟踪关联由 bootstrap 的安全过滤链契约验证。
      assertThat(problem.get("traceId").asString()).isEqualTo("unavailable");
      assertThat(problem.get("recoverable").asBoolean()).isEqualTo(recoverable);
      assertThat(response.getStatus()).isEqualTo(status);
      assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @Test
  void writesValidProblemJsonForControlCharactersAndQuotes() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    SecurityProblemWriter.write(response, 403, "ACCESS_DENIED", "line1\n\"line2\"");

    JsonNode problem = mapper.readTree(response.getContentAsString());
    assertThat(problem.get("detail").asString()).isEqualTo("line1\n\"line2\"");
    assertThat(problem.get("traceId").asString()).isEqualTo("unavailable");
    assertThat(problem.get("status").asInt()).isEqualTo(403);
    assertThat(problem.get("recoverable").asBoolean()).isFalse();
    assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
  }
}
