package com.ezaigc.hr.reporting.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.ezaigc.hr.reporting.mapper.MetricMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MetricServiceImplTest {

  private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-09-08T00:00:00Z");

  @Test
  void capturesOnlyRequestedEventTypesForTheRequestedWindow() {
    MetricMapper mapper = mock(MetricMapper.class);
    MetricServiceImpl service = service(mapper);

    service.capture(FROM, TO, List.of("RESUME_INGESTED", "ONBOARDED"));

    verify(mapper).captureResumeIngested(FROM, TO);
    verify(mapper).captureOnboarded(FROM, TO);
    verify(mapper, never()).captureResumeParsed(FROM, TO);
    verify(mapper, never()).captureApplications(FROM, TO);
    verify(mapper, never()).captureHumanScreened(FROM, TO);
    verify(mapper, never()).captureSubmissions(FROM, TO);
    verify(mapper, never()).captureInterviewPassed(FROM, TO);
  }

  @Test
  void rejectsUnknownEventTypesBeforeWritingMetrics() {
    MetricMapper mapper = mock(MetricMapper.class);
    MetricServiceImpl service = service(mapper);

    assertThatThrownBy(() -> service.capture(FROM, TO, List.of("RESUME_INGESTED", "UNKNOWN")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN");
    verifyNoInteractions(mapper);
  }

  @Test
  void capturesAllKnownTypesOnlyOnce() {
    MetricMapper mapper = mock(MetricMapper.class);
    MetricServiceImpl service = service(mapper);

    service.capture(FROM, TO, List.of("RESUME_INGESTED", "RESUME_PARSED", "APPLICATION_CREATED",
        "HUMAN_SCREENED", "SUBMISSION_SENT", "INTERVIEW_PASSED", "ONBOARDED", "RESUME_INGESTED"));

    verify(mapper).captureResumeIngested(FROM, TO);
    verify(mapper).captureResumeParsed(FROM, TO);
    verify(mapper).captureApplications(FROM, TO);
    verify(mapper).captureHumanScreened(FROM, TO);
    verify(mapper).captureSubmissions(FROM, TO);
    verify(mapper).captureInterviewPassed(FROM, TO);
    verify(mapper).captureOnboarded(FROM, TO);
    verifyNoMoreInteractions(mapper);
  }

  @Test
  void rejectsNullTypesBeforeAnyWrite() {
    MetricMapper mapper = mock(MetricMapper.class);
    MetricServiceImpl service = service(mapper);

    assertThatThrownBy(() -> service.capture(FROM, TO, Arrays.asList("RESUME_INGESTED", null)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper);
  }

  private static MetricServiceImpl service(MetricMapper mapper) {
    MetricServiceImpl service = new MetricServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    return service;
  }
}
