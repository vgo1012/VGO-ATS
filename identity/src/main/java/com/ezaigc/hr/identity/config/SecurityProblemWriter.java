package com.ezaigc.hr.identity.config;

import com.ezaigc.hr.common.infrastructure.json.JsonText;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;

public final class SecurityProblemWriter {

  private SecurityProblemWriter() {}

  public static void write(HttpServletResponse response, int status, String code, String detail)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/problem+json");
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", "urn:vgo:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    problem.put("title", status == 401 ? "Unauthorized" : "Forbidden");
    problem.put("status", status);
    problem.put("detail", detail);
    problem.put("code", code);
    problem.put("traceId", traceId());
    problem.put(
        "recoverable",
        status == 401 || code.startsWith("CSRF") || code.startsWith("PASSWORD"));
    response.getWriter().write(JsonText.write(problem));
  }

  private static String traceId() {
    String traceId = MDC.get("traceId");
    return traceId == null ? "unavailable" : traceId;
  }
}
