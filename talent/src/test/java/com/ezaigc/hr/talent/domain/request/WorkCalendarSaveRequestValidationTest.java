package com.ezaigc.hr.talent.domain.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.ArrayList;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class WorkCalendarSaveRequestValidationTest {
  @Test void initialSaveAllowsNullBaselineButValidatesNestedDaysAndSource() {
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator();
      var days = new ArrayList<>(LocalDate.of(2026, 1, 1).datesUntil(LocalDate.of(2027, 1, 1))
          .map(date -> new WorkCalendarSaveRequest.DayRequest(date, "WORKDAY", null)).toList());
      var request = new WorkCalendarSaveRequest(null, null, days, "通知", "初始设置");
      assertTrue(validator.validate(request).isEmpty());
      days.getFirst().setDayType("UNKNOWN");
      assertFalse(validator.validate(request).isEmpty());
      days.getFirst().setDayType("HOLIDAY");
      days.getFirst().setDate(null);
      assertFalse(validator.validate(request).isEmpty());
      days.getFirst().setDate(LocalDate.of(2026, 1, 1));
      days.set(0, null);
      assertFalse(validator.validate(request).isEmpty());
      days.set(0, new WorkCalendarSaveRequest.DayRequest(LocalDate.of(2026, 1, 1), "HOLIDAY", null));
      request.setSourceNote(" ");
      assertFalse(validator.validate(request).isEmpty());
      request.setSourceNote("通知");
      request.setExpectedVersion("-1");
      assertFalse(validator.validate(request).isEmpty());
    }
  }
}
