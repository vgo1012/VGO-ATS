package com.ezaigc.hr.integration.wecom;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.integration.config.WecomProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class WecomDirectoryAdapterTest {
    private final WecomProperties properties = new WecomProperties();
    private final WecomDirectoryAdapter adapter = new WecomDirectoryAdapter();
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        properties.setDirectoryEnabled(true);
        properties.setCorpId("test-corp");
        properties.setSecret("synthetic-test-secret");
        ReflectionTestUtils.setField(adapter, "properties", properties);
        ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/cgi-bin/", this::handle);
        server.start();
        ReflectionTestUtils.setField(adapter, "base", HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort() + "/cgi-bin/"));
        responses.put("gettoken", "{\"errcode\":0,\"access_token\":\"synthetic-token\",\"expires_in\":7200}");
        responses.put("department/simplelist", "{\"errcode\":0,\"department_id\":[{\"id\":2,\"parentid\":1},{\"id\":3,\"parentid\":2}]}");
        responses.put("department/get:2", "{\"errcode\":0,\"department\":{\"id\":2,\"parentid\":1,\"name\":\"人力中心\",\"order\":1}}");
        responses.put("department/get:3", "{\"errcode\":0,\"department\":{\"id\":3,\"parentid\":2,\"name\":\"招聘部\",\"order\":2}}");
        String users = """
                {"errcode":0,"userlist":[{"userid":"user-test","name":"测试成员","position":"HR",
                "department":[3,2],"status":1,"mobile":"private-mobile","email":"unverified@example.test",
                "extattr":{"private":"not-copied"}}]}
                """;
        responses.put("user/list:2", users);
        responses.put("user/list:3", users);
    }

    @AfterEach
    void tearDown() {
        adapter.close();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void disabledMissingConfigurationAndStatusNeverCallRemote() {
        properties.setDirectoryEnabled(false);
        assertThat(adapter.preview().getStatus()).isEqualTo("DISABLED");
        properties.setDirectoryEnabled(true);
        properties.setSecret("");
        assertThat(adapter.preview().getStatus()).isEqualTo("NOT_CONFIGURED");
        properties.setSecret("synthetic-test-secret");
        assertThat(adapter.status().getStatus()).isEqualTo("READY");
        assertThat(paths).isEmpty();
    }

    @Test
    void supports465DepartmentsAnd2586MembersWithoutTruncation() {
        StringBuilder identifiers = new StringBuilder("{\"errcode\":0,\"department_id\":[");
        int memberIndex = 0;
        for (int department = 1; department <= 465; department++) {
            if (department > 1) identifiers.append(',');
            identifiers.append("{\"id\":").append(department).append(",\"parentid\":0}");
            responses.put("department/get:" + department, "{\"errcode\":0,\"department\":{\"id\":"
                    + department + ",\"parentid\":0,\"name\":\"合成部门\",\"order\":0}}");
            StringBuilder members = new StringBuilder("{\"errcode\":0,\"userlist\":[");
            int size = department <= 261 ? 6 : 5;
            for (int index = 0; index < size; index++) {
                if (index > 0) members.append(',');
                members.append("{\"userid\":\"synthetic-").append(memberIndex++)
                        .append("\",\"name\":\"合成人员\",\"department\":[").append(department).append("]}");
            }
            responses.put("user/list:" + department, members.append("]}").toString());
        }
        responses.put("department/simplelist", identifiers.append("]}").toString());
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(result.getDepartments()).hasSize(465);
        assertThat(result.getMembers()).hasSize(2586);
        assertThat(paths).hasSize(932);
    }

    @Test
    void readsSourceLeadersButDoesNotInventMissingLeaderData() {
        responses.replaceAll((key, value) -> key.startsWith("user/list:")
                ? value.replace("\"status\":1", "\"status\":1,\"direct_leader\":[\"leader-b\",\"leader-a\"]") : value);
        var result = adapter.preview();
        assertThat(result.getMembers()).singleElement().satisfies(member ->
                assertThat(member.getDirectLeaderUserIds()).containsExactly("leader-a", "leader-b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1]", "[\"leader\",\"leader\"]", "\"not-array\"", "[\"\"]"})
    void rejectsMalformedSourceLeaderRelationships(String leaders) {
        responses.replaceAll((key, value) -> key.startsWith("user/list:")
                ? value.replace("\"status\":1", "\"status\":1,\"direct_leader\":" + leaders) : value);
        assertThat(adapter.preview().getStatus()).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    void enumeratesVisibleDepartmentsDeduplicatesMembersAndMinimizesFields() {
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(result.getDepartments()).hasSize(2);
        assertThat(result.getDepartments().getFirst().isParentOutsideScope()).isTrue();
        assertThat(result.getDepartments().getLast().isParentOutsideScope()).isFalse();
        assertThat(result.getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getDepartmentIds()).containsExactly(2L, 3L);
            assertThat(member.getBusinessEmail()).isNull();
        });
        String serialized = new ObjectMapper().writeValueAsString(result);
        assertThat(serialized).doesNotContain("private-mobile", "unverified@example.test", "not-copied", "synthetic-token");
        assertThat(paths).containsExactly("gettoken", "department/simplelist", "department/get:2", "user/list:2", "department/get:3", "user/list:3");
        assertThat(adapter.status().getMembers()).isEmpty();
        assertThat(adapter.preview().getStatus()).isEqualTo("COOLDOWN");
        assertThat(paths).hasSize(6);
    }

    @ParameterizedTest
    @CsvSource({"60020,IP_NOT_ALLOWED", "48002,PERMISSION_DENIED", "45009,RATE_LIMITED", "40013,CREDENTIALS_REJECTED", "-1,REMOTE_REJECTED"})
    void remoteFailuresAreSanitizedAndCooledDown(int code, String status) {
        responses.put("gettoken", "{\"errcode\":" + code + ",\"errmsg\":\"synthetic-test-secret 192.0.2.1 trace private\"}");
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getMessage()).doesNotContain("synthetic-test-secret", "192.0.2.1", "trace private");
        assertThat(result.getMembers()).isEmpty();
        assertThat(result.getRetryAfterSeconds()).isPositive();
        assertThat(adapter.status().getStatus()).isEqualTo(status);
        assertThat(adapter.preview().getStatus()).isEqualTo("COOLDOWN");
        assertThat(paths).containsExactly("gettoken");
    }

    @Test
    void failedLastDepartmentNeverReturnsPartialSnapshot() {
        responses.put("user/list:3", "{\"errcode\":48002}");
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo("PERMISSION_DENIED");
        assertThat(result.getDepartments()).isEmpty();
        assertThat(result.getMembers()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"errcode\":0}", "{\"errcode\":0,\"department_id\":null}",
            "{\"errcode\":0,\"department_id\":[{\"id\":2,\"parentid\":3},{\"id\":3,\"parentid\":2}]}",
            "{\"errcode\":0,\"department_id\":[{\"id\":2,\"parentid\":1},{\"id\":2,\"parentid\":1}]}",
            "<html>private-upstream-error</html>"})
    void incompleteMalformedDuplicateOrCyclicTreeFailsClosed(String body) {
        responses.put("department/simplelist", body);
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo("INVALID_RESPONSE");
        assertThat(result.getDepartments()).isEmpty();
        assertThat(paths).containsExactly("gettoken", "department/simplelist");
    }

    @Test
    void emptyScopeIsNotACompleteEmptyDirectory() {
        responses.put("department/simplelist", "{\"errcode\":0,\"department_id\":[]}");
        assertThat(adapter.preview().getStatus()).isEqualTo("EMPTY_SCOPE");
    }

    @Test
    void changedMembershipDuringTraversalIsRejected() {
        responses.put("user/list:3", "{\"errcode\":0,\"userlist\":[]}");
        assertThat(adapter.preview().getStatus()).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    void departmentLimitRejectsTruncationBeforeDetailCalls() {
        properties.setMaxDepartments(1);
        assertThat(adapter.preview().getStatus()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(paths).containsExactly("gettoken", "department/simplelist");
    }

    @Test
    void repeatedUseridInsideOneDepartmentIsNotSilentlyDeduplicated() {
        responses.put("user/list:2", """
                {"errcode":0,"userlist":[{"userid":"same","name":"测试","department":[2]},
                {"userid":"same","name":"测试","department":[2]}]}
                """);
        assertThat(adapter.preview().getStatus()).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    void memberLimitRejectsEntirePreview() {
        properties.setMaxMembers(1);
        responses.put("user/list:2", """
                {"errcode":0,"userlist":[{"userid":"one","name":"测试一","department":[2]},
                {"userid":"two","name":"测试二","department":[2]}]}
                """);
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(result.getMembers()).isEmpty();
    }

    @Test
    void optionalMissingFieldsRemainUnknownAndBusinessEmailIsNotInferred() {
        responses.put("user/list:2", """
                {"errcode":0,"userlist":[{"userid":"looks-like-email@example.test","name":"测试一","department":[2]}]}
                """);
        responses.put("user/list:3", """
                {"errcode":0,"userlist":[{"userid":"two","name":"测试二","department":[3],"biz_mail":"verified@example.test"}]}
                """);
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(result.getMembers().getFirst().getBusinessEmail()).isNull();
        assertThat(result.getMembers().getFirst().getSourceStatus()).isNull();
        assertThat(result.getMembers().getLast().getBusinessEmail()).isEqualTo("verified@example.test");
    }

    @Test
    void oversizedResponseIsBoundedAndDoesNotLeakPayload() {
        responses.put("gettoken", " ".repeat(2 * 1024 * 1024 + 1));
        assertThat(adapter.preview().getStatus()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void successfulTokenRefreshContinuesReadOnlyPreview() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/cgi-bin/department/simplelist", exchange -> send(exchange,
                attempts.incrementAndGet() == 1 ? "{\"errcode\":42001}" : responses.get("department/simplelist")));
        assertThat(adapter.preview().getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(paths.stream().filter("gettoken"::equals).count()).isEqualTo(2);
    }

    @Test
    void failureCanBeRetriedAfterCooldownWithoutStalePartialResults() {
        String healthy = responses.get("user/list:3");
        responses.put("user/list:3", "{\"errcode\":48002}");
        assertThat(adapter.preview().getStatus()).isEqualTo("PERMISSION_DENIED");
        responses.put("user/list:3", healthy);
        ReflectionTestUtils.setField(adapter, "nextAttemptNanos", 0L);
        assertThat(adapter.preview().getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(adapter.status().getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(adapter.status().getMembers()).isEmpty();
    }

    @Test
    void invalidTokenRefreshesExactlyOnceAndNeverLoops() {
        responses.put("department/simplelist", "{\"errcode\":42001}");
        assertThat(adapter.preview().getStatus()).isEqualTo("CREDENTIALS_REJECTED");
        assertThat(paths).containsExactly("gettoken", "department/simplelist", "gettoken", "department/simplelist");
    }

    @Test
    void cachedTokenIsReusedAndReadCanRecoverAfterCooldown() {
        adapter.preview();
        ReflectionTestUtils.setField(adapter, "nextAttemptNanos", 0L);
        assertThat(adapter.preview().getStatus()).isEqualTo("PREVIEW_READY");
        assertThat(paths.stream().filter("gettoken"::equals).count()).isEqualTo(1);
    }

    @Test
    void concurrentPreviewDoesNotQueueOrDuplicateRemoteReads() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/cgi-bin/gettoken", exchange -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                send(exchange, responses.get("gettoken"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var first = executor.submit(adapter::preview);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(adapter.preview().getStatus()).isEqualTo("RUNNING");
            assertThat(adapter.status().getStatus()).isEqualTo("RUNNING");
        } finally {
            release.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo("PREVIEW_READY");
    }

    @ParameterizedTest
    @CsvSource({"1,30", "5,1"})
    void timeoutBoundsStalledResponseBodyAndReleasesPreviewLock(int requestSeconds, int previewSeconds) throws Exception {
        properties.setRequestTimeoutSeconds(requestSeconds);
        properties.setPreviewTimeoutSeconds(previewSeconds);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/cgi-bin/gettoken", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 1000);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var pending = executor.submit(adapter::preview);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(pending.get(3, TimeUnit.SECONDS).getStatus()).isEqualTo("NETWORK_UNAVAILABLE");
            assertThat(adapter.preview().getStatus()).isEqualTo("COOLDOWN");
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @CsvSource({"429,RATE_LIMITED", "503,HTTP_UNAVAILABLE"})
    void httpFailuresReturnStableStatusWithoutLeakingBody(int httpStatus, String expectedStatus) {
        server.createContext("/cgi-bin/gettoken", exchange -> {
            byte[] body = "private upstream trace".getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.sendResponseHeaders(httpStatus, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        var result = adapter.preview();
        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getMessage()).doesNotContain("private upstream trace");
    }

    @Test
    void redirectIsNotFollowedAndSecretsNeverReachAnotherEndpoint() throws IOException {
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/redirected", exchange -> {
            redirected.incrementAndGet();
            exchange.close();
        });
        server.createContext("/cgi-bin/gettoken", exchange -> {
            exchange.getResponseHeaders().add("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        assertThat(adapter.preview().getStatus()).isEqualTo("HTTP_UNAVAILABLE");
        assertThat(redirected.get()).isZero();
    }

    private void handle(HttpExchange exchange) throws IOException {
        HttpUrl url = HttpUrl.get("http://127.0.0.1" + exchange.getRequestURI());
        String key = exchange.getRequestURI().getPath().substring("/cgi-bin/".length());
        String department = url.queryParameter("department_id");
        String identifier = url.queryParameter("id");
        if (department != null || identifier != null) {
            key += ":" + (department != null ? department : identifier);
        }
        paths.add(key);
        send(exchange, responses.getOrDefault(key, "{\"errcode\":99999}"));
    }

    private void send(HttpExchange exchange, String response) throws IOException {
        try (exchange) {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
