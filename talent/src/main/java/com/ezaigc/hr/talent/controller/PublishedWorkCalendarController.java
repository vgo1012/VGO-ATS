package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.talent.application.manager.WorkCalendarApplicationManager;
import com.ezaigc.hr.talent.domain.response.PublishedWorkCalendarResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已登录用户的公司工作日历只读入口，不提供管理信息或草稿。 */
@RestController
@RequestMapping("/api/v1/work-calendars/{year}/published")
public class PublishedWorkCalendarController {
  @Resource private WorkCalendarApplicationManager manager;

  @GetMapping
  public PublishedWorkCalendarResponse published(@PathVariable @Min(2000) @Max(2100) int year) {
    return manager.published(year);
  }
}
