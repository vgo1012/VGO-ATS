package com.ezaigc.hr.integration.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ezaigc.hr.integration.config.WecomProperties;
import com.ezaigc.hr.recruiting.interview.api.InterviewExternalCalendarPort.Command;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class WecomInterviewExternalCalendarAdapterTest {
    private final WecomInterviewExternalCalendarAdapter adapter =
            new WecomInterviewExternalCalendarAdapter();
    private final WecomProperties properties = new WecomProperties();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String tokenResponse = "{\"errcode\":0,\"access_token\":\"synthetic-token\",\"expires_in\":7200}";
    private String createResponse = "{\"errcode\":0,\"meetingid\":\"meeting-id\"}";
    private boolean disconnectAfterCreateAccepted;
    private boolean rejectTokenRefreshAfterCreation;
    private int createHttpStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        properties.setCorpId("test-corp");
        properties.setSecret("synthetic-secret");
        properties.getCollaboration().setEnabled(true);
        properties.getCollaboration().setAgentId(1000014);
        ReflectionTestUtils.setField(adapter, "properties", properties);
        ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cgi-bin/", this::handle);
        server.start();
        ReflectionTestUtils.setField(adapter, "base",
                HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort() + "/cgi-bin/"));
    }

    @AfterEach
    void tearDown() {
        adapter.close();
        server.stop(0);
    }

    @Test
    void createsMeetingWithInviteesAndSafeSettings() {
        var result = adapter.create(command("WECOM_MEETING", null));
        assertThat(result.succeeded()).isTrue();
        assertThat(result.externalObjectId()).isEqualTo("meeting-id");
        assertThat(result.meetingCode()).isEqualTo("401530450");
        assertThat(result.meetingLink()).isEqualTo("https://meeting.example/join");
        assertThat(requests).hasSize(3);
        assertThat(requests.get(1))
                .contains("meeting/create", "\"admin_userid\":\"wecom-admin-1\"",
                        "\"userid\":[\"wecom-admin-1\",\"interviewer-2\"]",
                        "\"enable_waiting_room\":true", "\"remind_before\":[300]")
                .doesNotContain("synthetic-secret", "access_token");
        assertThat(requests.getLast()).contains("meeting/get_info", "\"meetingid\":\"meeting-id\"");
    }

    @Test
    void acceptedCreateWithoutResponseCannotBeMarkedRetryable() {
        disconnectAfterCreateAccepted = true;

        var result = adapter.create(command("WECOM_MEETING", null));

        assertThat(requests.stream().filter(value -> value.startsWith("meeting/create ")).count())
                .isEqualTo(1);
        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.unknownCreate()).isTrue();
    }

    @Test
    void successWithoutCreatedIdCannotBeMarkedRetryable() {
        createResponse = "{\"errcode\":0}";

        var result = adapter.create(command("WECOM_MEETING", null));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.unknownCreate()).isTrue();
        assertThat(requests.stream().filter(value -> value.startsWith("meeting/create ")).count())
                .isEqualTo(1);
    }

    @Test
    void preservesCreatedIdWhenDetailsTokenRefreshFails() {
        tokenResponse = "{\"errcode\":0,\"access_token\":\"synthetic-token\",\"expires_in\":0}";
        rejectTokenRefreshAfterCreation = true;

        assertThatCode(() -> {
            var result = adapter.create(command("WECOM_MEETING", null));
            assertThat(result.succeeded()).isTrue();
            assertThat(result.externalObjectId()).isEqualTo("meeting-id");
        }).doesNotThrowAnyException();
        assertThat(requests.stream().filter(value -> value.startsWith("meeting/create ")).count())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500})
    void httpFailureAfterCreatePostIsUnknown(int status) {
        createHttpStatus = status;
        var result = adapter.create(command("WECOM_MEETING", null));
        assertThat(result.unknownCreate()).isTrue();
        assertThat(result.retryable()).isFalse();
        assertThat(requests.stream().filter(value -> value.startsWith("meeting/create ")).count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not-json"})
    void invalidCreateResponseIsUnknown(String response) {
        createResponse = response;
        var result = adapter.create(command("WECOM_MEETING", null));
        assertThat(result.unknownCreate()).isTrue();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void validRemoteRejectionRemainsRetryableWithoutBeingUnknown() {
        createResponse = "{\"errcode\":45009}";
        var result = adapter.create(command("WECOM_MEETING", null));
        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.unknownCreate()).isFalse();
        assertThat(requests.stream().filter(value -> value.startsWith("meeting/create ")).count()).isEqualTo(1);
    }

    @Test
    void tokenFailureBeforeCreateIsConfirmedNotSent() {
        tokenResponse = "{\"errcode\":40014}";
        var result = adapter.create(command("WECOM_MEETING", null));
        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.unknownCreate()).isFalse();
        assertThat(requests.stream().filter(value -> value.startsWith("meeting/create ")).count()).isZero();
    }

    @Test
    void disablingCreationStillAllowsExistingMeetingCancellation() {
        properties.getCollaboration().setEnabled(false);
        assertThat(adapter.create(command("WECOM_MEETING", null)).errorCode())
                .isEqualTo("WECOM_CREATION_DISABLED");
        var result = adapter.cancel(command("WECOM_MEETING", "meeting-id"));
        assertThat(result.succeeded()).isTrue();
        assertThat(requests.getLast()).contains("meeting/cancel", "\"meetingid\":\"meeting-id\"");
    }

    @ParameterizedTest
    @CsvSource({"30, 2", "60, 2", "7200, 1"})
    void refreshesTokensInsideSafetyWindowAndReusesLongLivedTokens(
            int expiresIn, long tokenRequests) {
        tokenResponse = "{\"errcode\":0,\"access_token\":\"synthetic-token\",\"expires_in\":"
                + expiresIn + "}";

        assertThat(adapter.cancel(command("WECOM_MEETING", "meeting-id")).succeeded()).isTrue();
        assertThat(adapter.cancel(command("WECOM_MEETING", "meeting-id")).succeeded()).isTrue();

        assertThat(requests.stream().filter(value -> value.startsWith("gettoken ")).count())
                .isEqualTo(tokenRequests);
        assertThat(requests.stream().filter(value -> value.startsWith("meeting/cancel ")).count())
                .isEqualTo(2);
    }

    private Command command(String type, String id) {
        return new Command(type, id, "招聘面试", "后端工程师面试", "wecom-admin-1",
                List.of("wecom-admin-1", "interviewer-2"), Instant.parse("2026-09-18T07:00:00Z"),
                Instant.parse("2026-09-18T07:30:00Z"), "Asia/Shanghai", "线上", 300,
                true, false, true);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().replace("/cgi-bin/", "");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(path + " " + body);
        if ("meeting/create".equals(path) && disconnectAfterCreateAccepted) {
            exchange.close();
            return;
        }
        String response = switch (path) {
            case "gettoken" -> rejectTokenRefreshAfterCreation
                    && requests.stream().filter(value -> value.startsWith("gettoken ")).count() > 1
                    ? "{\"errcode\":40014}" : tokenResponse;
            case "meeting/create" -> createResponse;
            case "meeting/get_info" -> "{\"errcode\":0,\"meetingid\":\"meeting-id\",\"meeting_code\":\"401530450\",\"meeting_link\":\"https://meeting.example/join\"}";
            case "meeting/cancel" -> "{\"errcode\":0,\"errmsg\":\"ok\"}";
            default -> "{\"errcode\":48002}";
        };
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders("meeting/create".equals(path) ? createHttpStatus : 200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
