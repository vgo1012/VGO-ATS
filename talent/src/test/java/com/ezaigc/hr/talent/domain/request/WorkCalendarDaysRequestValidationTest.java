package com.ezaigc.hr.talent.domain.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class WorkCalendarDaysRequestValidationTest {
  @Test void acceptsThreeCalendarTypesAndRejectsUnknownValues() {
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator();
      var request = new WorkCalendarDaysRequest("0", List.of(LocalDate.of(2026, 10, 1)), "HOLIDAY", "国庆节");
      for (String type : List.of("WORKDAY", "RESTDAY", "HOLIDAY")) {
        request.setDayType(type);
        assertTrue(validator.validate(request).isEmpty(), type);
      }
      request.setDayType("UNKNOWN");
      assertFalse(validator.validate(request).isEmpty());
    }
  }
}
