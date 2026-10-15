package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.bootstrap.web.TraceIdFilter;
import com.ezaigc.hr.intelligence.matching.application.manager.ScreeningAdmissionApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.TalentSearchApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationCandidateFilterDTO;
import com.ezaigc.hr.talent.application.manager.CandidateLibraryApplicationManager;
import com.ezaigc.hr.talent.application.manager.CandidateRescoreApplicationManager;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 使用应用实际自动装配和安全过滤链，防止 standalone 测试漏掉默认异常处理器竞争。 */
@NoDatabasePlatformTest
class HttpProblemDetailContractTest {
  private static final String TRACE = "trace_http_contract_20260908";
  private static final String RECOVERY = "/api/v1/tasks/01ARZ3NDEKTSV4RRFFQ69G5FAV/search-recovery";
  private static final String RESCORE = "/api/v1/shared-candidate-reclaim-scores";
  private static final String RECLAIM = "/api/v1/shared-candidate-reclaims";
  private static final String ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String RESCORE_ITEM = "{\"candidateId\":\"" + ID
      + "\",\"resumeVersionId\":\"" + ID + "\"}";
  private static final String RECLAIM_ITEM = "{\"candidateId\":\"" + ID
      + "\",\"resumeVersionId\":\"" + ID + "\",\"scoreBindingId\":\"" + ID
      + "\",\"candidateVersion\":0}";
  private static final String RESCORE_BODY = "{\"jdVersionId\":\"" + ID + "\",\"items\":["
      + RESCORE_ITEM + "]}";
  private static final String RECLAIM_BODY = "{\"items\":[" + RECLAIM_ITEM + "]}";
  private static final String ADMISSION = "/api/v1/screening-admissions";
  private static final String ADMISSION_ITEM = "{\"candidateId\":\"" + ID
      + "\",\"resumeVersionId\":\"" + ID + "\",\"jdVersionId\":\"" + ID
      + "\",\"defaultMatchRunId\":\"" + ID + "\"}";
  private static final String ADMISSION_BODY = "{\"items\":[" + ADMISSION_ITEM + "]}";
  @Resource private MockMvc mvc;
  @MockitoBean private CandidateLibraryApplicationManager candidateLibrary;
  @MockitoBean private CandidateRescoreApplicationManager candidateRescore;
  @MockitoBean private ScreeningAdmissionApplicationManager screeningAdmissions;
  @MockitoBean private RecruitmentApplicationManager recruitment;
  @MockitoBean private TalentSearchApplicationManager talentSearch;

  static Stream<Arguments> invalidListIndexes() {
    return Stream.of("/api/v1/applications", "/api/v1/applications/count", "/api/v1/talent-search", "/api/v1/talent-search/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of("-1", "private-index-marker", "256", "2147483648", "''", "\"\"")
                .map(index -> Arguments.of(endpoint, field, field + "[" + index + "]"))));
  }

  @ParameterizedTest
  @MethodSource("invalidListIndexes")
  void invalidListIndexesFailBeforeTheQuery(String endpoint, String field, String parameter) throws Exception {
    var result = mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .header(TraceIdFilter.HEADER, TRACE).param(parameter, "private-filter-marker"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
        .andExpect(jsonPath("$.traceId").value(TRACE)).andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("private-index-marker", "private-filter-marker", "rejectedValue");
    verifyNoInteractions(recruitment, talentSearch);
  }

  static Stream<Arguments> validTalentListParameters() {
    return Stream.of("/api/v1/talent-search", "/api/v1/talent-search/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of(field, field + "[0]")
                .map(parameter -> Arguments.of(endpoint, field, parameter))));
  }

  @ParameterizedTest
  @MethodSource("validTalentListParameters")
  void ordinaryAndIndexedTalentFiltersReachTheQuery(String endpoint, String field, String parameter) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .param(parameter, " 本科 "))
        .andExpect(status().isOk());
    assertTalentFilter(endpoint, field);
  }

  static Stream<Arguments> quotedListIndexes() {
    return Stream.of("/api/v1/applications", "/api/v1/applications/count", "/api/v1/talent-search", "/api/v1/talent-search/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of(field + "['0']", field + "[\"0\"]")
                .map(parameter -> Arguments.of(endpoint, field, parameter))));
  }

  @ParameterizedTest
  @MethodSource("quotedListIndexes")
  void quotedListIndexesRemainValid(String endpoint, String field, String parameter) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .param("stage", "ONBOARDED").param(parameter, " 本科 "))
        .andExpect(status().isOk());
    assertNamedFilter(endpoint, field);
  }

  static Stream<Arguments> arrayStyleListParameters() {
    return Stream.of("/api/v1/applications", "/api/v1/applications/count", "/api/v1/talent-search", "/api/v1/talent-search/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of(field + "[]", field + "[0][]", field + "['0'][]", field + "[\"0\"][]")
                .map(parameter -> Arguments.of(endpoint, field, parameter))));
  }

  @ParameterizedTest
  @MethodSource("arrayStyleListParameters")
  void arrayStyleListParametersRemainValid(String endpoint, String field, String parameter) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .param("stage", "ONBOARDED").param(parameter, " 本科 "))
        .andExpect(status().isOk());
    assertNamedFilter(endpoint, field);
  }

  static Stream<Arguments> ignoredArrayStyleListParameters() {
    return Stream.of("/api/v1/applications", "/api/v1/applications/count", "/api/v1/talent-search", "/api/v1/talent-search/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of("-1", "private-index-marker", "256", "2147483648", "")
                .map(index -> Arguments.of(endpoint, field + "[" + index + "][]"))));
  }

  @ParameterizedTest
  @MethodSource("ignoredArrayStyleListParameters")
  void ignoredArrayStylesLeaveFiltersEmpty(String endpoint, String parameter) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .param("stage", "ONBOARDED").param(parameter, "private-filter-marker"))
        .andExpect(status().isOk());
    if (endpoint.startsWith("/api/v1/applications")) {
      assertApplicationFilter(endpoint, new String[] {}, new String[] {});
    } else {
      assertThat(mockingDetails(talentSearch).getInvocations()).singleElement().satisfies(invocation -> {
        SearchCriteria filter = invocation.getArgument(0);
        assertThat(filter.getConditions()).isEmpty();
        assertThat(filter.getDegrees()).isEmpty();
      });
    }
  }

  private void assertNamedFilter(String endpoint, String field) {
    if (endpoint.startsWith("/api/v1/applications")) {
      assertApplicationFilter(endpoint, "conditions".equals(field) ? new String[] {"本科"} : new String[] {},
          "degrees".equals(field) ? new String[] {"本科"} : new String[] {});
    } else {
      assertTalentFilter(endpoint, field);
    }
  }

  private void assertTalentFilter(String endpoint, String field) {
    assertThat(mockingDetails(talentSearch).getInvocations()).singleElement().satisfies(invocation -> {
      assertThat(invocation.getMethod().getName()).isEqualTo(endpoint.endsWith("/count") ? "count" : "search");
      SearchCriteria filter = invocation.getArgument(0);
      assertThat(filter.getConditions()).containsExactly("conditions".equals(field) ? new String[] {"本科"} : new String[] {});
      assertThat(filter.getDegrees()).containsExactly("degrees".equals(field) ? new String[] {"本科"} : new String[] {});
    });
  }

  static Stream<Arguments> validApplicationListParameters() {
    return Stream.of("/api/v1/applications", "/api/v1/applications/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of(field, field + "[0]")
                .map(parameter -> Arguments.of(endpoint, field, parameter))));
  }

  @ParameterizedTest
  @MethodSource("validApplicationListParameters")
  void ordinaryAndIndexedApplicationFiltersReachTheQuery(
      String endpoint, String field, String parameter) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .param("stage", "ONBOARDED").param(parameter, " 本科 "))
        .andExpect(status().isOk());
    assertApplicationFilter(endpoint, "conditions".equals(field) ? new String[] {"本科"} : new String[] {},
        "degrees".equals(field) ? new String[] {"本科"} : new String[] {});
  }

  static Stream<Arguments> invalidApplicationListParameters() {
    return Stream.of("/api/v1/applications", "/api/v1/applications/count")
        .flatMap(endpoint -> Stream.of("conditions", "degrees")
            .flatMap(field -> Stream.of(
                Arguments.of(endpoint, field + "[1]", "private-filter-marker", field + "[0]"),
                Arguments.of(endpoint, field + "[0]", "  ", field + "[0]"))));
  }

  @ParameterizedTest
  @MethodSource("invalidApplicationListParameters")
  void sparseAndBlankApplicationFiltersFailBeforeTheQuery(
      String endpoint, String parameter, String value, String field) throws Exception {
    var result = mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .header(TraceIdFilter.HEADER, TRACE).param("stage", "ONBOARDED").param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
        .andExpect(jsonPath("$.traceId").value(TRACE)).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("private-filter-marker", "rejectedValue");
    verifyNoInteractions(recruitment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/applications", "/api/v1/applications/count"})
  void missingApplicationFiltersRemainEmpty(String endpoint) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN")))
        .andExpect(status().isOk());
    assertApplicationFilter(endpoint, new String[] {}, new String[] {});
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/applications", "/api/v1/applications/count"})
  void repeatedApplicationFiltersStillNormalizeAndDeduplicate(String endpoint) throws Exception {
    mvc.perform(get(endpoint).with(user("test-admin").roles("SUPER_ADMIN"))
            .param("stage", "ONBOARDED").param("conditions", " UPS ", "RAG", "UPS")
            .param("degrees", " 本科 ", "硕士", "本科"))
        .andExpect(status().isOk());
    assertApplicationFilter(endpoint, new String[] {"RAG", "UPS"}, new String[] {"本科", "硕士"});
  }

  private void assertApplicationFilter(String endpoint, String[] conditions, String[] degrees) {
    assertThat(mockingDetails(recruitment).getInvocations()).singleElement().satisfies(invocation -> {
      boolean count = endpoint.endsWith("/count");
      assertThat(invocation.getMethod().getName()).isEqualTo(count ? "countApplications" : "applicationResponses");
      ApplicationCandidateFilterDTO filter = invocation.getArgument(count ? 9 : 10);
      assertThat(filter.getConditions()).containsExactly(conditions);
      assertThat(filter.getDegrees()).containsExactly(degrees);
    });
  }

  static Stream<Arguments> nullScreeningAdmissionItems() {
    return Stream.of(
        Arguments.of("{\"items\":[null]}", 0),
        Arguments.of(ADMISSION_BODY.replace("]}", ",null]}"), 1));
  }

  @ParameterizedTest
  @MethodSource("nullScreeningAdmissionItems")
  void rejectsNullScreeningAdmissionItemsBeforeCallingTheCommand(String body, int index)
      throws Exception {
    var result = mvc.perform(post(ADMISSION).with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", "admission-null-item").header(TraceIdFilter.HEADER, TRACE)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("items[" + index + "]"))
        .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
        .andExpect(jsonPath("$.traceId").value(TRACE)).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(ID, "rejectedValue");
    verifyNoInteractions(screeningAdmissions);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 1000})
  void validScreeningAdmissionItemsStillReachTheCommand(int count) throws Exception {
    String body = ADMISSION_BODY.replace(ADMISSION_ITEM,
        String.join(",", Collections.nCopies(count, ADMISSION_ITEM)));
    mvc.perform(post(ADMISSION).with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", "admission-valid-item")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    verify(screeningAdmissions).admit(argThat(items -> items.size() == count
        && ID.equals(items.getFirst().getCandidateId())
        && ID.equals(items.getLast().getResumeVersionId())
        && ID.equals(items.getLast().getJdVersionId())
        && ID.equals(items.getLast().getDefaultMatchRunId())), eq("admission-valid-item"));
  }

  static Stream<Arguments> otherInvalidScreeningAdmissionItems() {
    Stream<Arguments> outer = Stream.of(
        Arguments.of("{}", "items"),
        Arguments.of("{\"items\":null}", "items"),
        Arguments.of("{\"items\":[]}", "items"),
        Arguments.of(ADMISSION_BODY.replace(ADMISSION_ITEM,
            String.join(",", Collections.nCopies(1001, ADMISSION_ITEM))), "items"));
    Stream<Arguments> fields = Stream.of("candidateId", "resumeVersionId", "jdVersionId", "defaultMatchRunId")
        .map(field -> Arguments.of(ADMISSION_BODY.replace(
            "\"" + field + "\":\"" + ID + "\"", "\"" + field + "\":null"), "items[0]." + field));
    return Stream.concat(outer, fields);
  }

  @ParameterizedTest
  @MethodSource("otherInvalidScreeningAdmissionItems")
  void keepsScreeningAdmissionSizeAndNestedFieldValidation(String body, String field) throws Exception {
    var result = mvc.perform(post(ADMISSION).with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", "admission-invalid-item")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
        .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty()).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(ID, "rejectedValue");
    verifyNoInteractions(screeningAdmissions);
  }

  static Stream<Arguments> nullCandidateLibraryItems() {
    return Stream.of(
        Arguments.of(RESCORE, "{\"jdVersionId\":\"" + ID + "\",\"items\":[null]}", 0),
        Arguments.of(RECLAIM, "{\"items\":[null]}", 0),
        Arguments.of(RESCORE, RESCORE_BODY.replace("]}", ",null]}"), 1),
        Arguments.of(RECLAIM, RECLAIM_BODY.replace("]}", ",null]}"), 1));
  }

  @ParameterizedTest
  @MethodSource("nullCandidateLibraryItems")
  void rejectsNullBatchItemsBeforeCallingBusinessCommands(String endpoint, String body, int index)
      throws Exception {
    mvc.perform(post(endpoint).with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", "batch-null-item").header(TraceIdFilter.HEADER, TRACE)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("items[" + index + "]"))
        .andExpect(jsonPath("$.traceId").value(TRACE));
    verifyNoInteractions(candidateLibrary, candidateRescore);
  }

  static Stream<Arguments> validCandidateLibraryItems() {
    return Stream.of(
        Arguments.of(RESCORE, RESCORE_BODY, 1), Arguments.of(RECLAIM, RECLAIM_BODY, 1),
        Arguments.of(RESCORE, RESCORE_BODY.replace(RESCORE_ITEM,
            String.join(",", Collections.nCopies(100, RESCORE_ITEM))), 100),
        Arguments.of(RECLAIM, RECLAIM_BODY.replace(RECLAIM_ITEM,
            String.join(",", Collections.nCopies(100, RECLAIM_ITEM))), 100));
  }

  @ParameterizedTest
  @MethodSource("validCandidateLibraryItems")
  void validBatchItemsStillReachTheirBusinessCommand(String endpoint, String body, int count) throws Exception {
    mvc.perform(post(endpoint).with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", "batch-valid-item")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    if (endpoint.equals(RESCORE)) {
      verify(candidateRescore).rescore(argThat(request -> ID.equals(request.getJdVersionId())
          && request.getItems().size() == count && ID.equals(request.getItems().getFirst().getCandidateId())),
          eq("batch-valid-item"));
      verifyNoInteractions(candidateLibrary);
    } else {
      verify(candidateLibrary).reclaim(argThat(request -> request.getItems().size() == count
          && ID.equals(request.getItems().getFirst().getScoreBindingId())
          && request.getItems().getFirst().getCandidateVersion() == 0), eq("batch-valid-item"));
      verifyNoInteractions(candidateRescore);
    }
  }

  static Stream<Arguments> otherInvalidCandidateLibraryItems() {
    return Stream.of(
        Arguments.of(RESCORE, "{\"jdVersionId\":\"" + ID + "\",\"items\":null}", "items"),
        Arguments.of(RECLAIM, "{\"items\":null}", "items"),
        Arguments.of(RESCORE, RESCORE_BODY.replace(RESCORE_ITEM, ""), "items"),
        Arguments.of(RECLAIM, RECLAIM_BODY.replace(RECLAIM_ITEM, ""), "items"),
        Arguments.of(RESCORE, RESCORE_BODY.replace(RESCORE_ITEM,
            String.join(",", Collections.nCopies(101, RESCORE_ITEM))), "items"),
        Arguments.of(RECLAIM, RECLAIM_BODY.replace(RECLAIM_ITEM,
            String.join(",", Collections.nCopies(101, RECLAIM_ITEM))), "items"),
        Arguments.of(RESCORE, RESCORE_BODY.replace("\"candidateId\":\"" + ID + "\"",
            "\"candidateId\":\"private-field-marker\""), "items[0].candidateId"),
        Arguments.of(RECLAIM, RECLAIM_BODY.replace("\"candidateVersion\":0",
            "\"candidateVersion\":null"), "items[0].candidateVersion"));
  }

  @ParameterizedTest
  @MethodSource("otherInvalidCandidateLibraryItems")
  void batchBoundsAndNestedValidationStillRejectInvalidRequests(String endpoint, String body, String field)
      throws Exception {
    var result = mvc.perform(post(endpoint).with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header("Idempotency-Key", "batch-invalid-item")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
        .andExpect(jsonPath("$.fieldErrors[0].field").value(field)).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(ID, "private-field-marker", "rejectedValue");
    verifyNoInteractions(candidateLibrary, candidateRescore);
  }

  static Stream<Arguments> clientErrors() {
    return Stream.of(
        Arguments.of(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"workEmail\":\"test@example.invalid\"}"), 400, "VALIDATION_FAILED"),
        Arguments.of(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"),
            400, "REQUEST_BODY_INVALID"),
        Arguments.of(get("/api/v1/tasks").param("limit", "0"), 400, "VALIDATION_FAILED"),
        Arguments.of(get("/api/v1/tasks").param("limit", "test-private-marker"), 400, "VALIDATION_FAILED"),
        Arguments.of(post(RECOVERY).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"恢复\"}"),
            400, "VALIDATION_FAILED"),
        Arguments.of(post(RECOVERY).header("Idempotency-Key", "contains space")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"恢复\"}"), 400, "VALIDATION_FAILED"),
        Arguments.of(post(RECOVERY).header("Idempotency-Key", "test-recovery")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"), 400, "VALIDATION_FAILED"),
        Arguments.of(get("/api/v1/auth/login"), 405, "METHOD_NOT_ALLOWED"),
        Arguments.of(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("test-private-marker"),
            415, "UNSUPPORTED_MEDIA_TYPE"),
        Arguments.of(get("/api/v1/does-not-exist"), 404, "RESOURCE_NOT_FOUND"),
        Arguments.of(get("/api/v1/auth/csrf").accept(MediaType.TEXT_PLAIN), 406, "NOT_ACCEPTABLE"));
  }

  @ParameterizedTest
  @MethodSource("clientErrors")
  void actualMvcErrorsKeepPlatformContract(MockHttpServletRequestBuilder request, int expectedStatus, String code)
      throws Exception {
    var result = mvc.perform(request.with(user("test-admin").roles("SUPER_ADMIN")).with(csrf())
            .header(TraceIdFilter.HEADER, TRACE))
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string(TraceIdFilter.HEADER, TRACE))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.traceId").value(TRACE))
        .andExpect(jsonPath("$.recoverable").value(true)).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("test-private-marker", "Exception", "stackTrace");
    if (expectedStatus == 405) assertThat(result.getResponse().getHeader("Allow")).contains("POST");
    if (expectedStatus == 415) assertThat(result.getResponse().getHeader("Accept")).contains("application/json");
  }

  @Test
  void authenticationAndCsrfStillUseSecurityProblemContract() throws Exception {
    mvc.perform(get("/api/v1/tasks").header(TraceIdFilter.HEADER, TRACE))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
        .andExpect(header().string(TraceIdFilter.HEADER, TRACE)).andExpect(jsonPath("$.traceId").value(TRACE));
    mvc.perform(post(RECOVERY).with(user("test-admin")).header(TraceIdFilter.HEADER, TRACE))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
        .andExpect(header().string(TraceIdFilter.HEADER, TRACE)).andExpect(jsonPath("$.traceId").value(TRACE));
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "unsafe trace marker"})
  void rejectedAnonymousRequestGetsSafeTraceAndCleansThreadContext(String input) throws Exception {
    var request = get("/api/v1/tasks");
    if (!input.isEmpty()) request.header(TraceIdFilter.HEADER, input);
    var response = mvc.perform(request).andExpect(status().isUnauthorized()).andReturn().getResponse();
    String trace = response.getHeader(TraceIdFilter.HEADER);
    assertThat(trace).matches("[a-f0-9]{32}");
    assertThat(response.getContentAsString()).contains("\"traceId\":\"" + trace + "\"").doesNotContain("unsafe trace marker");
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }
}
