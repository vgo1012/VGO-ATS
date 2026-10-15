package com.ezaigc.hr.common.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class SafeLogExceptionTest {
  @Test
  void preservesDeclaredJsonPropertyPathWithoutLeakingPayload() {
    JacksonException failure;
    try {
      JsonMapper.builder().build().writeValueAsString(List.of(new BrokenResponse()));
      throw new AssertionError("Expected serialization failure");
    } catch (JacksonException exception) {
      failure = exception;
    }

    String output = stackTrace(SafeLogException.from(failure));
    assertThat(output).contains("jsonPath=$[0].email")
        .doesNotContain("audit-person@example.invalid");
  }

  @Test
  void redactsDynamicJsonKeysAndBoundsDiagnosticPath() {
    JacksonException failure = JacksonException.wrapWithPath(
        new IllegalArgumentException("private payload"), Map.of(), "email");
    failure.prependPath(new BrokenResponse(), "audit-person@example.invalid");
    String output = stackTrace(SafeLogException.from(failure));
    assertThat(output).contains("jsonPath=$[?][?]")
        .doesNotContain("audit-person@example.invalid", "private payload", ".email");
    for (int i = 0; i < 100; i++) failure.prependPath(List.of(), i);
    assertThat(SafeLogException.from(failure).getMessage()).hasSizeLessThan(300).endsWith("...]");
  }

  public static final class BrokenResponse {
    public String getEmail() {
      throw new IllegalArgumentException("audit-person@example.invalid");
    }
  }

  @Test
  void preservesSafeJdbcDiagnosticsWithoutCopyingSqlOrParameterValues() {
    SQLException next = new SQLException(
        "Duplicate entry 'audit-person@example.invalid' for key 'uk_candidate_email'",
        "23000",
        1062);
    BatchUpdateException source = new BatchUpdateException(
        "INSERT INTO talent_candidate(primary_email) VALUES ('audit-person@example.invalid')",
        "HY000",
        3819,
        new int[0]);
    source.setNextException(next);

    String output = stackTrace(SafeLogException.from(source));

    assertThat(output)
        .contains("sqlState=HY000", "errorCode=3819", "category=check-constraint-violation")
        .contains("sqlState=23000", "errorCode=1062", "category=duplicate-key")
        .doesNotContain("audit-person@example.invalid", "INSERT INTO", "uk_candidate_email");
  }

  @Test
  void rejectsUntrustedSqlStateText() {
    SQLException source = new SQLException("private message", "private-state", 99999);

    String output = stackTrace(SafeLogException.from(source));

    assertThat(output).contains("errorCode=99999", "category=database-error")
        .doesNotContain("private message", "private-state");
  }

  @Test
  void preservesTypesAndFramesWithoutCopyingAnyExceptionMessages() {
    IllegalArgumentException cause = new IllegalArgumentException("15500000000");
    IllegalStateException source = new IllegalStateException("audit-person@example.invalid", cause);
    // 用确定的短栈验证逐帧保留，长栈上限由独立用例覆盖。
    source.setStackTrace(new StackTraceElement[] {source.getStackTrace()[0]});
    cause.setStackTrace(new StackTraceElement[] {cause.getStackTrace()[0]});
    source.addSuppressed(new UnsupportedOperationException("test-secret-marker"));
    SafeLogException safe = SafeLogException.from(source);
    StringWriter output = new StringWriter();
    safe.printStackTrace(new PrintWriter(output));

    assertThat(output.toString()).contains("java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
        "java.lang.UnsupportedOperationException", "SafeLogExceptionTest")
        .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    assertThat(safe.getStackTrace()).containsExactly(source.getStackTrace());
    assertThat(safe.getCause().getStackTrace()).containsExactly(cause.getStackTrace());
    assertThat(source.getMessage()).isEqualTo("audit-person@example.invalid");
    assertThat(source.getSuppressed()[0].getMessage()).isEqualTo("test-secret-marker");
  }

  @Test
  void handlesCyclicAndRepeatedReferencesWithoutRetainingOriginalThrowables() {
    RuntimeException first = new RuntimeException("first private text");
    RuntimeException second = new RuntimeException("second private text");
    first.initCause(second);
    second.initCause(first);
    first.addSuppressed(second);

    SafeLogException safe = SafeLogException.from(first);
    assertThat(safe.getCause()).isNotSameAs(second);
    assertThat(safe.getCause().getCause()).isNull();
    assertThat(safe.getSuppressed()).isEmpty();
    assertThat(first.getCause()).isSameAs(second);
    assertThat(second.getCause()).isSameAs(first);
  }

  @Test
  void boundsLargeGraphsAndStacks() {
    RuntimeException root = new RuntimeException("private root");
    StackTraceElement[] frames = new StackTraceElement[1000];
    Arrays.fill(frames, new StackTraceElement("Service", "process", "Service.java", 10));
    for (int i = 0; i < 100; i++) {
      RuntimeException child = new RuntimeException("private value " + i);
      child.setStackTrace(frames);
      root.addSuppressed(child);
    }
    ArrayDeque<Throwable> remaining = new ArrayDeque<>();
    remaining.add(SafeLogException.from(root));
    int nodes = 0;
    while (!remaining.isEmpty()) {
      Throwable current = remaining.remove();
      nodes++;
      assertThat(current.getStackTrace()).hasSizeLessThanOrEqualTo(64);
      remaining.addAll(Arrays.asList(current.getSuppressed()));
      if (current.getCause() != null) {
        remaining.add(current.getCause());
      }
    }
    assertThat(nodes).isEqualTo(32);
    assertThat(root.getSuppressed()).hasSize(100);
  }

  private static String stackTrace(Throwable failure) {
    StringWriter output = new StringWriter();
    failure.printStackTrace(new PrintWriter(output));
    return output.toString();
  }
}
