package com.ezaigc.hr.bootstrap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.FieldViolation;
import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.intelligence.search.domain.request.TalentSearchRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.sql.SQLException;
import java.util.Locale;
import java.util.List;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.io.IOException;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.client.ResourceAccessException;
import org.hamcrest.Matchers;

class GlobalExceptionHandlerTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void methodValidationKeepsMixedErrorsAndSafeFallbacks(boolean discoverParameterName) throws Exception {
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("batchInput", Object.class);
        var parameter = new MethodParameter(method, 0);
        parameter.initParameterNameDiscovery(discoverParameterName ? new DefaultParameterNameDiscoverer() : null);
        var input = new Object();
        var errors = new BeanPropertyBindingResult(input, "batch");
        errors.addError(new FieldError("batch", "items[1].candidateId", "private-field-marker", false,
                new String[] {"NotBlank"}, new Object[] {"private-field-marker"}, " "));
        errors.addError(new ObjectError("batch", new String[] {"InvalidBatch"},
                new Object[] {"private-field-marker"}, "请求不一致"));
        var result = new ParameterErrors(parameter, input, errors, null, null, null);
        var crossError = new DefaultMessageSourceResolvable(new String[] {"CrossParameter"},
                new Object[] {"private-field-marker"}, null);
        var validation = MethodValidationResult.create(this, method, List.of(result), List.of(crossError));
        var response = new GlobalExceptionHandler().handleHandlerMethodValidationException(
                new HandlerMethodValidationException(validation), HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest("POST", "/test/batch")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        var problem = (ProblemDetail) response.getBody();
        assertThat(problem.getProperties().get("fieldErrors")).isEqualTo(List.of(
                new FieldViolation("items[1].candidateId", "参数校验失败"),
                new FieldViolation(discoverParameterName ? "request" : "arg0", "请求不一致"),
                new FieldViolation("request", "参数校验失败")));
        assertThat(new ObjectMapper().writeValueAsString(problem)).doesNotContain("private-field-marker", "rejectedValue");
        assertThat(logAppender.list).isEmpty();
    }

    private static void batchInput(Object request) {
        // 为原生方法校验结果提供真实参数元数据，不创建额外HTTP路由。
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    @ResourceLock(Resources.LOCALE)
    void keepsProblemTypesStableAcrossExceptionPathsAndDefaultLocales(String languageTag) throws Exception {
        Locale previous = Locale.getDefault();
        Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            mockMvc.perform(get("/test/failure").header(TraceIdFilter.HEADER, "trace_1234567890abcdef"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("urn:vgo:problem:version-conflict"))
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                    .andExpect(jsonPath("$.traceId").value("trace_1234567890abcdef"))
                    .andExpect(jsonPath("$.instance").value("/test/failure"));
            mockMvc.perform(get("/test/validated").param("limit", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("urn:vgo:problem:validation-failed"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));
            mockMvc.perform(get("/test/framework-failure"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "7"))
                    .andExpect(jsonPath("$.type").value("urn:vgo:problem:external-dependency-unavailable"))
                    .andExpect(jsonPath("$.code").value("EXTERNAL_DEPENDENCY_UNAVAILABLE"));
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.type").value("urn:vgo:problem:internal-error"))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("系统暂时无法完成请求"));
        } finally {
            Locale.setDefault(previous);
            Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
            Locale.setDefault(Locale.Category.FORMAT, previousFormat);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disconnectedJsonResponseIsHandledWithoutAnotherWriteOrWarning(boolean committed) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/work-calendars/2026");
        var response = new MockHttpServletResponse();
        response.setCommitted(committed);
        var failure = new HttpMessageNotWritableException("private response content",
                new AsyncRequestNotUsableException("ServletOutputStream failed to write",
                        new ClientAbortException(new IOException("你的主机中的软件中止了一个已建立的连接。"))));

        assertThat(new GlobalExceptionHandler().handleException(failure, new ServletWebRequest(request, response)))
                .isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(logAppender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @Test
    void unexpectedClientAbortDoesNotProduceProblemBody() {
        assertThat(new GlobalExceptionHandler().handleUnexpected(
                new ClientAbortException(new IOException("private disconnected message")),
                new MockHttpServletRequest())).isNull();
        assertThat(logAppender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realSerializationFailureStaysVisibleWithoutLeakingCommittedResponseDetails(boolean committed) {
        var response = new MockHttpServletResponse();
        response.setCommitted(committed);
        var result = new GlobalExceptionHandler().handleExceptionInternal(
                new HttpMessageNotWritableException("private serialization data", new IllegalStateException("secret")),
                null, HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR,
                new ServletWebRequest(new MockHttpServletRequest(), response));
        if (committed) {
            assertThat(result).isNull();
        } else {
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(((ProblemDetail) result.getBody()).getProperties()).containsEntry("code", "INTERNAL_ERROR");
        }
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).doesNotContain("private serialization data");
            assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy())).doesNotContain("secret");
        });
    }

    @Test
    void outboundConnectionFailureRemainsServerError() {
        var problem = new GlobalExceptionHandler().handleUnexpected(
                new ResourceAccessException("upstream failed", new IOException("Connection reset by peer")),
                new MockHttpServletRequest());
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(logAppender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    private MockMvc mockMvc;
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger.addAppender(logAppender);
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void returnsRfcProblemDetailsWithStableCodeAndTraceId() throws Exception {
        mockMvc.perform(get("/test/failure").header(TraceIdFilter.HEADER, "trace_1234567890abcdef"))
                .andExpect(status().isConflict())
                .andExpect(header().string(TraceIdFilter.HEADER, "trace_1234567890abcdef"))
                .andExpect(jsonPath("$.type").value("urn:vgo:problem:version-conflict"))
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.traceId").value("trace_1234567890abcdef"))
                .andExpect(jsonPath("$.recoverable").value(true));

        assertThat(logAppender.list).as("预期内的 4xx 业务冲突不应污染错误日志").isEmpty();
    }

    @Test
    void replacesUnsafeInboundTraceIds() throws Exception {
        mockMvc.perform(get("/test/failure").header(TraceIdFilter.HEADER, "bad trace"))
                .andExpect(status().isConflict())
                .andExpect(header().string(TraceIdFilter.HEADER, Matchers.matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.traceId", Matchers.matchesPattern("[a-f0-9]{32}")));
    }

    @Test
    void mapsControllerMethodValidationToBadRequestWithoutErrorLog() throws Exception {
        mockMvc.perform(get("/test/validated").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.recoverable").value(true))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));

        assertThat(logAppender.list).as("客户端参数校验失败不应记录为系统错误").isEmpty();
    }

    @Test
    void rejectsMalformedIdempotencyHeaderAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/test/idempotency").header("Idempotency-Key", "contains space"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("key"));

        assertThat(logAppender.list).as("客户端幂等键格式错误不应记录为系统错误").isEmpty();
    }

    @Test
    void rejectsMalformedPlatformIdentifierAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/test/platform-id/short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));

        assertThat(logAppender.list).as("客户端资源 ID 格式错误不应记录为系统错误").isEmpty();
    }

    @Test
    void validatesOptionalPlatformIdentifierOnlyWhenSupplied() throws Exception {
        mockMvc.perform(get("/test/optional-platform-id"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/test/optional-platform-id").param("ownerUserId", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("ownerUserId"));

        assertThat(logAppender.list).as("客户端可选资源 ID 格式错误不应记录为系统错误").isEmpty();
    }

    @Test
    void rejectsUnsafeOptionalFilterCodeAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/test/filter-code"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/test/filter-code").param("status", "<script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

        mockMvc.perform(get("/test/filter-code").param("status", "ACTIVE\r\nforged-log-entry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(logAppender.list).as("客户端筛选代码格式错误不应记录为系统错误").isEmpty();
    }

    @Test
    void rejectsMalformedCursorAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/test/cursor").param("cursor", "unsafe/cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"));

        assertThat(logAppender.list).as("客户端游标格式错误不应记录为系统错误").isEmpty();
    }

    @Test
    void rejectsOversizedStandardPageAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/test/page-limit").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));

        assertThat(logAppender.list).as("客户端分页大小错误不应记录为系统错误").isEmpty();
    }

    @Test
    void logsKnownServerFailuresWithRequestCorrelationAndCause() {
        IllegalStateException rootCause = privateFailure();
        PlatformException exception = new PlatformException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE",
                "依赖服务暂时不可用",
                true,
                rootCause);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/infrastructure-failure");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/test/infrastructure-failure");

        var response = new GlobalExceptionHandler().handlePlatformException(exception, request);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getDetail()).isEqualTo("依赖服务暂时不可用");

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("DEPENDENCY_UNAVAILABLE", "/test/infrastructure-failure", "unavailable");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertPrivateMessagesAbsent(event);
        });
        assertThat(rootCause.getMessage()).isEqualTo("audit-person@example.invalid");
    }

    @Test
    void unexpectedHttpFailureRetainsCorrelationWithoutPrivateExceptionMessages() throws Exception {
        mockMvc.perform(get("/test/unexpected").header(TraceIdFilter.HEADER, "trace_1234567890abcdef"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("系统暂时无法完成请求"))
                .andExpect(jsonPath("$.traceId").value("trace_1234567890abcdef"));

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                    .contains("/test/unexpected", "trace_1234567890abcdef");
            assertPrivateMessagesAbsent(event);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void businessIndexFailuresRemainServerErrors(int kind) throws Exception {
        mockMvc.perform(get("/test/index-failure").param("conditions[0]", "valid").param("kind", Integer.toString(kind)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("系统暂时无法完成请求"));
    }

    @Test
    void unexpectedDatabaseFailureLogsSafeJdbcDiagnostics() {
        SQLException databaseFailure = new SQLException(
                "Check constraint 'ck_talent_intake_status' rejected private candidate data", "HY000", 3819);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/intake-batches/private/files");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/intake-batches/{id}/files");

        new GlobalExceptionHandler().handleUnexpected(databaseFailure, request);

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("/api/v1/intake-batches/{id}/files");
            assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    .contains("sqlState=HY000", "errorCode=3819", "category=check-constraint-violation")
                    .doesNotContain("private candidate data", "ck_talent_intake_status");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"validated", "idempotency"})
    void missingRequiredValuesKeepFieldErrors(String endpoint) throws Exception {
        mockMvc.perform(get("/test/" + endpoint))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(endpoint.equals("validated") ? "limit" : "Idempotency-Key"));
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void frameworkUploadLimitRetainsPayloadTooLargeStatus() throws Exception {
        mockMvc.perform(get("/test/upload-limit"))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("FILE_REJECTED"));
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void invalidControllerReturnValueRemainsServerFailure() throws Exception {
        mockMvc.perform(get("/test/invalid-result"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        assertThat(logAppender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void frameworkServerFailureKeepsRetryHeaderButRemovesPrivateDetail() throws Exception {
        var result = mockMvc.perform(get("/test/framework-failure").header(TraceIdFilter.HEADER, "trace_1234567890abcdef"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("EXTERNAL_DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value("trace_1234567890abcdef")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-secret-marker", "audit-person@example.invalid");
        assertThat(logAppender.list).singleElement().satisfies(GlobalExceptionHandlerTest::assertPrivateMessagesAbsent);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestFailureLogsRouteTemplateWithoutDownloadCredentials(boolean known) throws Exception {
        String credential = "test-secret-marker-15500000000";
        String path = "/test/download-authorizations/" + credential;
        mockMvc.perform(get(path).param("known", Boolean.toString(known))
                        .header(TraceIdFilter.HEADER, "trace_1234567890abcdef"))
                .andExpect(status().is(known ? 503 : 500))
                .andExpect(jsonPath("$.code").value(known ? "DEPENDENCY_UNAVAILABLE" : "INTERNAL_ERROR"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.traceId").value("trace_1234567890abcdef"));
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                    .contains("/test/download-authorizations/{token}", "trace_1234567890abcdef")
                    .doesNotContain(credential, "15500000000", "test-secret-marker");
            assertPrivateMessagesAbsent(event);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingRouteTemplateNeverFallsBackToPrivateRequestPath(boolean known) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private/test-secret-marker-15500000000");
        if (known) {
            new GlobalExceptionHandler().handlePlatformException(new PlatformException(
                    HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "依赖服务暂时不可用", true,
                    privateFailure()), request);
        } else {
            new GlobalExceptionHandler().handleUnexpected(privateFailure(), request);
        }
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("path=unmapped")
                    .doesNotContain("/private/", "15500000000", "test-secret-marker");
            assertPrivateMessagesAbsent(event);
        });
    }

    private static void assertPrivateMessagesAbsent(ILoggingEvent event) {
        assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                .contains("java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
                        "GlobalExceptionHandlerTest.java")
                .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    }

    private static IllegalStateException privateFailure() {
        IllegalStateException failure = new IllegalStateException(
                "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
        failure.addSuppressed(new RuntimeException("test-secret-marker"));
        return failure;
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "audit-person@example.invalid", "15500000000"})
    void invalidNumericParametersReturnSafeClientErrorsWithoutLoggingRawInput(String input) throws Exception {
        var result = mockMvc.perform(get("/test/page-limit").param("limit", input))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(input, "NumberFormatException");
        assertThat(logAppender.list).isEmpty();
    }

    @RestController
    static class FailureController {

        @GetMapping("/test/framework-failure")
        void frameworkFailure() {
            var exception = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "test-secret-marker"), privateFailure());
            exception.getHeaders().set("Retry-After", "7");
            throw exception;
        }

        @GetMapping("/test/invalid-result")
        @NotNull
        String invalidResult() { return null; }

        @GetMapping("/test/upload-limit")
        void uploadLimit() { throw new MaxUploadSizeExceededException(1024); }

        @GetMapping("/test/download-authorizations/{token}")
        void downloadFailure(@RequestParam boolean known) {
            if (known) {
                throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                        "依赖服务暂时不可用", true, privateFailure());
            }
            throw privateFailure();
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw privateFailure();
        }

        @GetMapping("/test/index-failure")
        void indexFailure(@ModelAttribute TalentSearchRequest request, @RequestParam int kind) {
            throw switch (kind) {
                case 0 -> new IndexOutOfBoundsException("private-index-marker");
                case 1 -> new NumberFormatException("private-index-marker");
                default -> new InvalidPropertyException(TalentSearchRequest.class, "conditions", "private-index-marker");
            };
        }

        @GetMapping("/test/failure")
        void failure() {
            throw new PlatformException(
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT",
                    "数据已更新，请重新确认",
                    true);
        }

        @GetMapping("/test/validated")
        void validated(@RequestParam @Min(1) int limit) {
            // 仅用于验证 Spring MVC 方法参数校验的统一错误契约。
        }

        @GetMapping("/test/idempotency")
        void idempotency(@ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
            // 仅用于验证组合约束会在 HTTP 边界执行。
        }

        @GetMapping("/test/platform-id/{id}")
        void platformIdentifier(@PlatformIdentifier @PathVariable String id) {
            // 仅用于验证资源 ID 组合约束会在 HTTP 边界执行。
        }

        @GetMapping("/test/optional-platform-id")
        void optionalPlatformIdentifier(
                @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId) {
            // 仅用于验证未传可选 ID 时不触发约束，显式传入时执行格式校验。
        }

        @GetMapping("/test/filter-code")
        void filterCode(@OptionalFilterCode @RequestParam(required = false) String status) {
            // 仅用于验证可选筛选代码的长度和字符约束会在 HTTP 边界执行。
        }

        @GetMapping("/test/cursor")
        void cursor(@ValidCursor @RequestParam(required = false) String cursor) {
            // 仅用于验证分页游标组合约束会在 HTTP 边界执行。
        }

        @GetMapping("/test/page-limit")
        void pageLimit(@StandardPageLimit @RequestParam int limit) {
            // 仅用于验证分页大小组合约束会在 HTTP 边界执行。
        }
    }
}
