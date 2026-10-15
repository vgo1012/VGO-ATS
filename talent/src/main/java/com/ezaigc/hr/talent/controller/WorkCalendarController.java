package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.talent.application.manager.WorkCalendarApplicationManager;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarDayDTO;
import com.ezaigc.hr.talent.domain.request.WorkCalendarSaveRequest;
import com.ezaigc.hr.talent.domain.response.WorkCalendarRevisionResponse;
import com.ezaigc.hr.talent.domain.response.WorkCalendarYearResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公司工作日历管理员入口；每个用例由应用层复核 SUPER_ADMIN。 */
@RestController
@RequestMapping("/api/v1/admin/work-calendars/{year}")
public class WorkCalendarController {
  @Resource private WorkCalendarApplicationManager manager;

  @GetMapping
  public WorkCalendarYearResponse year(@PathVariable @Min(2000) @Max(2100) int year) {
    return manager.year(year);
  }

  @PutMapping
  public WorkCalendarRevisionResponse save(@PathVariable @Min(2000) @Max(2100) int year,
      @Valid @RequestBody WorkCalendarSaveRequest request,
      @RequestHeader("Idempotency-Key") @ValidIdempotencyKey String key) {
    return manager.save(year, request.getPublishedRevisionId(), request.getExpectedVersion(),
        request.getDays().stream().map(day -> new WorkCalendarDayDTO(
            day.getDate(), day.getDayType(), day.getDescription())).toList(),
        request.getSourceNote(), request.getReason(), key);
  }

  @GetMapping("/history")
  public List<WorkCalendarRevisionResponse> history(@PathVariable @Min(2000) @Max(2100) int year) {
    return manager.history(year);
  }
}
