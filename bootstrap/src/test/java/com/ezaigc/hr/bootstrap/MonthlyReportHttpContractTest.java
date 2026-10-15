package com.ezaigc.hr.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportApplicationManager;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportDetailResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportFileResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportPageResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportSummaryResponse;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 月报 HTTP 层保留 multipart、版本和幂等键的线路合同，授权由应用服务作最终裁决。 */
@NoDatabasePlatformTest
class MonthlyReportHttpContractTest {
  private static final String ID = "01M1KFNYEV1VMB8D7Q69S6V622";
  private static final String KEY = "monthly-report-upload-7";
  private static final String BASE = "/api/v1/monthly-reports";

  @Resource private MockMvc mvc;
  @MockitoBean private MonthlyReportApplicationManager manager;

  @Test
  void deleteAcceptsVersionAndReasonWithoutAnIdempotencyHeader() throws Exception {
    mvc.perform(delete(BASE + "/" + ID).with(user("director").roles("HR_DIRECTOR")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"7\",\"reason\":\"重复导入\"}"))
        .andExpect(status().isNoContent());
    verify(manager).delete(eq(ID), argThat(request ->
        "7".equals(request.getVersion()) && "重复导入".equals(request.getReason())));
  }

  @Test
  void deleteRejectsMissingReasonAndMalformedVersionBeforeApplication() throws Exception {
    for (String body : List.of("{\"version\":\"7\"}", "{\"version\":\"1.5\",\"reason\":\"清理\"}",
        "{\"version\":\"7\",\"reason\":\" \"}")) {
      mvc.perform(delete(BASE + "/" + ID).with(user("director").roles("HR_DIRECTOR")).with(csrf())
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(manager);
  }

  @Test
  void deleteRequiresAuthenticationAndCsrf() throws Exception {
    String body = "{\"version\":\"7\",\"reason\":\"清理\"}";
    mvc.perform(delete(BASE + "/" + ID).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete(BASE + "/" + ID).with(user("director").roles("HR_DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    verifyNoInteractions(manager);
  }

  @Test
  void uploadAcceptsBothWorkbookPartsAndReturnsTheMonthlyReportShape() throws Exception {
    when(manager.upload(any(), any(), eq("2026-08"), eq("2026-08-31"), eq(KEY)))
        .thenReturn(detail());

    mvc.perform(multipart(BASE)
            .file(workbook("offerFile", "offer.xlsx"))
            .file(workbook("demandFile", "demand.xlsx"))
            .param("month", "2026-08")
            .param("asOfDate", "2026-08-31")
            .header("Idempotency-Key", KEY)
            .with(user("director").roles("HR_DIRECTOR"))
            .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.month").value("2026-08"))
        .andExpect(jsonPath("$.asOfDate").value("2026-08-31"))
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.version").value("7"))
        .andExpect(jsonPath("$.createTime").value("2026-09-01T00:00:00Z"))
        .andExpect(jsonPath("$.createName").value("负责人"))
        .andExpect(jsonPath("$.offerCount").value(2))
        .andExpect(jsonPath("$.files[0].kind").value("OFFER"))
        .andExpect(jsonPath("$.files[0].fileName").value("offer.xlsx"));

    verify(manager).upload(any(), any(), eq("2026-08"), eq("2026-08-31"), eq(KEY));
  }

  @Test
  void generateRejectsMalformedVersionBeforeTheApplicationManager() throws Exception {
    mvc.perform(post(BASE + "/" + ID + "/generate")
            .with(user("director").roles("HR_DIRECTOR"))
            .with(csrf())
            .header("Idempotency-Key", KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":\"1.5\",\"acknowledgeWarnings\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verifyNoInteractions(manager);
  }

  @Test
  void uploadRequiresAuthenticationAndCsrfBeforeTheApplicationManager() throws Exception {
    mvc.perform(multipart(BASE)
            .file(workbook("offerFile", "offer.xlsx"))
            .file(workbook("demandFile", "demand.xlsx"))
            .param("month", "2026-08")
            .param("asOfDate", "2026-08-31")
            .header("Idempotency-Key", KEY)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(multipart(BASE)
            .file(workbook("offerFile", "offer.xlsx"))
            .file(workbook("demandFile", "demand.xlsx"))
            .param("month", "2026-08")
            .param("asOfDate", "2026-08-31")
            .header("Idempotency-Key", KEY)
            .with(user("director").roles("HR_DIRECTOR")))
        .andExpect(status().isForbidden());

    verifyNoInteractions(manager);
  }

  @Test
  void uploadRejectsAnEmptyWorkbookBeforeTheApplicationManager() throws Exception {
    assertUploadRejected(new MockMultipartFile("offerFile", "offer.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]), workbook("demandFile", "demand.xlsx"), KEY);
  }

  @Test
  void uploadRejectsANonXlsxWorkbookBeforeTheApplicationManager() throws Exception {
    assertUploadRejected(workbook("offerFile", "offer.xls"), workbook("demandFile", "demand.xlsx"), KEY);
  }

  @Test
  void uploadRejectsAPathBearingWorkbookNameBeforeTheApplicationManager() throws Exception {
    assertUploadRejected(workbook("offerFile", "../offer.xlsx"), workbook("demandFile", "demand.xlsx"), KEY);
  }

  @Test
  void uploadRejectsAnOverlongWorkbookNameBeforeTheApplicationManager() throws Exception {
    assertUploadRejected(workbook("offerFile", "a".repeat(196) + ".xlsx"), workbook("demandFile", "demand.xlsx"), KEY);
  }

  @Test
  void uploadRejectsAnOversizedWorkbookBeforeTheApplicationManager() throws Exception {
    assertUploadRejected(new MockMultipartFile("offerFile", "offer.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[10 * 1024 * 1024 + 1]),
        workbook("demandFile", "demand.xlsx"), KEY);
  }

  @Test
  void uploadRejectsAnInvalidIdempotencyKeyBeforeTheApplicationManager() throws Exception {
    assertUploadRejected(workbook("offerFile", "offer.xlsx"), workbook("demandFile", "demand.xlsx"), "invalid key");
  }

  @Test
  void listRejectsAnInvalidCursorBeforeTheApplicationManager() throws Exception {
    mvc.perform(get(BASE).param("cursor", "invalid*cursor").with(user("director").roles("HR_DIRECTOR")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manager);
  }

  @Test
  void applicationAuthorizationDenialKeepsThePlatformProblemContract() throws Exception {
    doThrow(PlatformExceptions.forbidden("MONTHLY_REPORT_FORBIDDEN", "仅人力负责人和管理员可以使用招聘月度汇报"))
        .when(manager).list(null, null, 20);

    mvc.perform(get(BASE).with(user("ordinary-hr").roles("RECRUITER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MONTHLY_REPORT_FORBIDDEN"));

    verify(manager).list(null, null, 20);
  }

  @Test
  void listKeepsThePageResponseShape() throws Exception {
    when(manager.list("2026-08", null, 20)).thenReturn(new MonthlyReportPageResponse(
        List.of(new MonthlyReportSummaryResponse(ID, "2026-08", LocalDate.of(2026, 8, 31), "READY", "7",
            Instant.parse("2026-09-01T00:00:00Z"), "负责人", 2, new BigDecimal("122.75"), null, null)), "next"));

    mvc.perform(get(BASE).param("month", "2026-08").with(user("director").roles("HR_DIRECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(ID))
        .andExpect(jsonPath("$.items[0].version").value("7"))
        .andExpect(jsonPath("$.nextCursor").value("next"));
  }

  private static MockMultipartFile workbook(String part, String name) {
    return new MockMultipartFile(part, name,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1, 2, 3});
  }

  private void assertUploadRejected(MockMultipartFile offer, MockMultipartFile demand, String key) throws Exception {
    mvc.perform(multipart(BASE)
            .file(offer)
            .file(demand)
            .param("month", "2026-08")
            .param("asOfDate", "2026-08-31")
            .header("Idempotency-Key", key)
            .with(user("director").roles("HR_DIRECTOR"))
            .with(csrf()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manager);
  }

  private static MonthlyReportDetailResponse detail() {
    return new MonthlyReportDetailResponse(ID, "2026-08", LocalDate.of(2026, 8, 31), "READY", "7",
        Instant.parse("2026-09-01T00:00:00Z"), "负责人", 2, new BigDecimal("122.75"), null, null,
        "01M1KFNYEV1VMB8D7Q69S6V623", 100, false, "", List.of(new MonthlyReportFileResponse("OFFER", "offer.xlsx", 3)),
        List.of(), null);
  }
}
