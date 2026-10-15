package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarCalculationDTO;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.service.WorkCalendarPersistenceService;
import com.ezaigc.hr.talent.enums.WorkCalendarDayType;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 按不可变已发布年度计算；调用方持有简历锁后，再按升序持有年度指针锁到业务提交。 */
@Service
public class WorkCalendarCalculationManager {
  @Resource private WorkCalendarPersistenceService calendars;

  public WorkCalendarCalculationDTO calculate(
      LocalDate enteredDate, LocalDate settlementDate, boolean lockPointers) {
    return calculateWithOverride(enteredDate, settlementDate, lockPointers, null, null);
  }

  /** 仅用于管理员发布预览：替换指定年度，其他年度仍读取已发布版本。 */
  public WorkCalendarCalculationDTO calculateWithOverride(
      LocalDate enteredDate, LocalDate settlementDate, boolean lockPointers,
      Integer overrideYear, String overrideRevisionId) {
    if (lockPointers && !TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("结算日历锁必须在流转事务内获取");
    }
    if (enteredDate == null || settlementDate == null) {
      throw PlatformExceptions.badRequest("WORK_CALENDAR_DATE_REQUIRED", "进入日期与结算日期不能为空");
    }
    Map<Integer, String> vector = new LinkedHashMap<>();
    List<Integer> missing = new ArrayList<>();
    List<LocalDate> thresholds = new ArrayList<>();
    int elapsed = 0;
    Instant latestPublishedAt = null;
    boolean complete = true;
    boolean thresholdGap = false;
    LocalDate first = enteredDate.plusDays(1);
    // 即使管理员把连续年度设为休息，也持续到首个未发布年度，返回明确的缺失说明。
    int lastYear = Math.max(2101, settlementDate.getYear() + 1);
    for (int year = first.getYear(); year <= lastYear; year++) {
      // 四个阈值已知后，不无故要求未涉及的未来年度。
      if (year > settlementDate.getYear() && thresholds.size() == 4) break;
      WorkCalendarYearEntity pointer;
      if (lockPointers) {
        calendars.ensureYear(year);
        pointer = calendars.lockYear(year);
      } else {
        pointer = calendars.findYear(year);
      }
      String revisionId = overrideYear != null && year == overrideYear
          ? overrideRevisionId : pointer == null ? null : pointer.getPublishedRevisionId();
      if (revisionId == null) {
        missing.add(year);
        if (year <= settlementDate.getYear() && !first.isAfter(settlementDate)) complete = false;
        thresholdGap = true;
        if (year > settlementDate.getYear()) break;
        continue;
      }
      boolean draftOverride = overrideYear != null && year == overrideYear;
      if (!draftOverride && year <= settlementDate.getYear() && !first.isAfter(settlementDate)) {
        // 指针锁持续到流转提交；发布若先提交读取新版本，发布若后到只能等待本条提交。
        WorkCalendarRevisionEntity revision = calendars.getById(revisionId);
        if (revision == null || revision.getPublishedAt() == null
            || revision.getCalendarYear() == null || revision.getCalendarYear() != year) {
          throw PlatformExceptions.conflict("WORK_CALENDAR_INCOMPLETE", "已发布日历版本或发布时间无效");
        }
        if (latestPublishedAt == null || revision.getPublishedAt().isAfter(latestPublishedAt)) {
          latestPublishedAt = revision.getPublishedAt();
        }
      }
      List<WorkCalendarDayEntity> days = calendars.days(revisionId);
      validateCoverage(year, days);
      vector.put(year, revisionId);
      for (WorkCalendarDayEntity day : days) {
        LocalDate date = day.getCalendarDate();
        if (date.isBefore(first) || !WorkCalendarDayType.WORKDAY.matches(day.getDayType())) continue;
        if (!date.isAfter(settlementDate)) elapsed++;
        if (!thresholdGap && thresholds.size() < 4) thresholds.add(date);
      }
    }
    return new WorkCalendarCalculationDTO(elapsed, vector, missing, thresholds, complete, latestPublishedAt);
  }

  public static void validateCoverage(int year, List<WorkCalendarDayEntity> days) {
    LocalDate start = LocalDate.of(year, 1, 1);
    Map<LocalDate, WorkCalendarDayEntity> unique = new HashMap<>();
    for (WorkCalendarDayEntity day : days) {
      LocalDate date = day.getCalendarDate();
      if (date == null || date.getYear() != year || unique.put(date, day) != null
          || !(WorkCalendarDayType.WORKDAY.matches(day.getDayType()) || WorkCalendarDayType.RESTDAY.matches(day.getDayType()) || WorkCalendarDayType.HOLIDAY.matches(day.getDayType()))) {
        throw PlatformExceptions.conflict("WORK_CALENDAR_INCOMPLETE", "日历日期重复、类型无效或超出年份");
      }
    }
    if (unique.size() != start.lengthOfYear()) {
      throw PlatformExceptions.conflict("WORK_CALENDAR_INCOMPLETE", "全年日历必须完整覆盖每一天");
    }
  }
}
