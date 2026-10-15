package com.ezaigc.hr.talent.domain.dto;
import com.ezaigc.hr.talent.enums.ResumeLifecyclePhase;
import com.ezaigc.hr.talent.enums.ResumeLifecycleWarning;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
/** 工作日数由已发布日历提供；此处只定义结算边界与阈值。 */
public final class ResumeLifecycleTiming {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private ResumeLifecycleTiming() {}
  public static LocalDate settlementDate(Instant now) {
    var local = now.atZone(ZONE);
    return local.toLocalDate().minusDays(local.getHour() < 1 ? 2 : 1);
  }
  public static String phase(int days) {
    return days >= 4 ? ResumeLifecyclePhase.SHARED.getCode() : days >= 1 ? ResumeLifecyclePhase.PENDING_DISPOSAL.getCode() : ResumeLifecyclePhase.PENDING_SCREENING.getCode();
  }
  public static String warning(int days) { return days >= 3 ? ResumeLifecycleWarning.RED.getCode() : days == 2 ? ResumeLifecycleWarning.YELLOW.getCode() : ResumeLifecycleWarning.NORMAL.getCode(); }
  public static Instant transitionAt(LocalDate workday) { return workday.plusDays(1).atTime(1,0).atZone(ZONE).toInstant(); }
}
