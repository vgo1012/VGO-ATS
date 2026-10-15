package com.ezaigc.hr.bootstrap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.application.manager.WorkCalendarApplicationManager;
import com.ezaigc.hr.talent.domain.request.WorkCalendarSaveRequest;
import com.ezaigc.hr.talent.domain.response.WorkCalendarRevisionResponse;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 真实安全链与 HTTP 输入契约；应用服务替身不作为数据库事务/方言验证。 */
@NoDatabasePlatformTest
class WorkCalendarSaveHttpTest {
  private static final String URL = "/api/v1/admin/work-calendars/2026";
  private static final String ID = "01M1KFNYEV1VMB8D7Q69S6V622";
  private static final String KEY = "calendar-save-1";
  @Resource private MockMvc mvc;
  @Resource private ObjectMapper json;
  @MockitoBean private WorkCalendarApplicationManager manager;

  @Test void firstSaveForwardsNullBaselineAndFullYearAndReturnsPublishedRevision() throws Exception {
    var response = new WorkCalendarRevisionResponse();
    response.setId(ID); response.setYear(2026); response.setStatus("PUBLISHED"); response.setVersion("0");
    when(manager.save(eq(2026), isNull(), isNull(), anyList(), eq("官方通知"), eq("初始设置"), eq(KEY)))
        .thenReturn(response);
    mvc.perform(put(URL).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request())))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.id").value(ID)).andExpect(jsonPath("$.version").value("0"));
    verify(manager).save(eq(2026), isNull(), isNull(),
        argThat(days -> days.size() == 365 && days.getFirst().getDate().equals(LocalDate.of(2026, 1, 1))
            && "WORKDAY".equals(days.getFirst().getDayType())), eq("官方通知"), eq("初始设置"), eq(KEY));
  }

  @Test void editingForwardsPublishedIdAndVersionAndConflictIsRecoverable() throws Exception {
    var request = request(); request.setPublishedRevisionId(ID); request.setExpectedVersion("3");
    when(manager.save(eq(2026), eq(ID), eq("3"), anyList(), anyString(), anyString(), eq(KEY)))
        .thenThrow(PlatformExceptions.versionConflict("WORK_CALENDAR_VERSION_CONFLICT"));
    mvc.perform(put(URL).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORK_CALENDAR_VERSION_CONFLICT"));
  }

  @Test void securityRequiresLoginSuperAdminAndCsrf() throws Exception {
    String body = json.writeValueAsString(request());
    mvc.perform(put(URL).with(csrf()).header("Idempotency-Key", KEY)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    mvc.perform(put(URL).with(user("hr").roles("RECRUITER")).with(csrf())
        .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    mvc.perform(put(URL).with(user("admin").roles("SUPER_ADMIN"))
        .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    verifyNoInteractions(manager);
  }

  @Test void missingOrMalformedIdempotencyKeyIsRejectedBeforeApplication() throws Exception {
    String body = json.writeValueAsString(request());
    mvc.perform(put(URL).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    mvc.perform(put(URL).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
        .header("Idempotency-Key", "invalid key").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test void nestedDaysMetadataAndVersionAreValidatedBeforeApplication() throws Exception {
    var unknownType = request(); unknownType.getDays().getFirst().setDayType("UNKNOWN");
    var missingDays = request(); missingDays.setDays(List.of());
    var nullDate = request(); nullDate.getDays().getFirst().setDate(null);
    var missingSource = request(); missingSource.setSourceNote(" ");
    var invalidVersion = request(); invalidVersion.setExpectedVersion("-1");
    for (var invalid : List.of(unknownType, missingDays, nullDate, missingSource, invalidVersion)) {
      mvc.perform(put(URL).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
          .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(manager);
  }

  private WorkCalendarSaveRequest request() {
    return new WorkCalendarSaveRequest(null, null,
        LocalDate.of(2026, 1, 1).datesUntil(LocalDate.of(2027, 1, 1))
            .map(date -> new WorkCalendarSaveRequest.DayRequest(date, "WORKDAY", null)).toList(),
        "官方通知", "初始设置");
  }
}
