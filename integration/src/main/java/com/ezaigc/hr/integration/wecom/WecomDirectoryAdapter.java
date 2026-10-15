package com.ezaigc.hr.integration.wecom;

import com.ezaigc.hr.identity.api.WecomDepartmentDTO;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.api.WecomMemberDTO;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import com.ezaigc.hr.integration.config.WecomProperties;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class WecomDirectoryAdapter implements WecomDirectoryPort {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    @Resource private WecomProperties properties;
    @Resource private ObjectMapper json;
    private final AtomicBoolean running = new AtomicBoolean();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private HttpUrl base = HttpUrl.get("https://qyapi.weixin.qq.com/cgi-bin/");
    private volatile WecomDirectoryResultDTO lastOutcome;
    private volatile long nextAttemptNanos;
    private String accessToken;
    private long tokenExpiresNanos;

    @Override
    public WecomDirectoryResultDTO status() {
        WecomDirectoryResultDTO blocked = configurationProblem();
        if (blocked != null) {
            return blocked;
        }
        if (running.get()) {
            return result("RUNNING", "当前实例已有组织预览正在执行，请勿重复触发", 1);
        }
        WecomDirectoryResultDTO previous = lastOutcome;
        if (previous != null) {
            return new WecomDirectoryResultDTO(previous.getStatus(), previous.getMessage(),
                    previous.getCorpId(), previous.getObservedAt(), remainingCooldown(), List.of(), List.of());
        }
        return result("READY", "配置已填写，尚未验证企业微信连接或权限", 0);
    }

    @Override
    public WecomDirectoryResultDTO preview() {
        WecomDirectoryResultDTO blocked = configurationProblem();
        if (blocked != null) {
            return blocked;
        }
        if (!running.compareAndSet(false, true)) {
            return result("RUNNING", "当前实例已有组织预览正在执行，请勿重复触发", 1);
        }
        try {
            long cooldown = remainingCooldown();
            if (cooldown > 0) {
                return result("COOLDOWN", "组织预览正在冷却，请按提示时间重试；可查询状态查看上次结果", cooldown);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getPreviewTimeoutSeconds());
            WecomDirectoryResultDTO outcome;
            try {
                outcome = readDirectory(deadline);
            } catch (WecomRemoteFailure failure) {
                outcome = result(failure.status(), failure.getMessage(), properties.getCooldownSeconds());
            }
            nextAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getCooldownSeconds());
            lastOutcome = new WecomDirectoryResultDTO(outcome.getStatus(), outcome.getMessage(),
                    outcome.getCorpId(), outcome.getObservedAt(), properties.getCooldownSeconds(), List.of(), List.of());
            return outcome;
        } finally {
            running.set(false);
        }
    }

    private WecomDirectoryResultDTO readDirectory(long deadline) {
        JsonNode identifiers = array(get("department/simplelist", Map.of(), deadline).get("department_id"));
        if (identifiers.isEmpty()) {
            throw new WecomRemoteFailure("EMPTY_SCOPE", "应用未返回可见部门，请核实授权范围；不能据此停用任何账号");
        }
        if (identifiers.size() > properties.getMaxDepartments()) {
            throw new WecomRemoteFailure("LIMIT_EXCEEDED", "可见部门数量超过预览保护上限，请调整配置后重试");
        }
        Map<Long, JsonNode> departmentTree = new LinkedHashMap<>();
        for (JsonNode identifier : identifiers) {
            long departmentId = positiveLong(identifier.get("id"));
            nonnegativeLong(identifier.get("parentid"));
            if (departmentTree.putIfAbsent(departmentId, identifier) != null) {
                throw WecomRemoteFailure.invalidResponse();
            }
        }
        validateTree(departmentTree);
        List<WecomDepartmentDTO> departments = new ArrayList<>();
        Map<String, WecomMemberDTO> members = new LinkedHashMap<>();
        Map<String, Set<Long>> observedMemberships = new LinkedHashMap<>();
        for (Map.Entry<Long, JsonNode> entry : departmentTree.entrySet()) {
            ensureDeadline(deadline);
            long departmentId = entry.getKey();
            JsonNode detail = get("department/get", Map.of("id", Long.toString(departmentId)), deadline).get("department");
            if (detail == null || positiveLong(detail.get("id")) != departmentId) {
                throw WecomRemoteFailure.invalidResponse();
            }
            long parentId = nonnegativeLong(detail.get("parentid"));
            if (parentId != nonnegativeLong(entry.getValue().get("parentid"))) {
                throw WecomRemoteFailure.invalidResponse();
            }
            departments.add(new WecomDepartmentDTO(departmentId, parentId, requiredText(detail, "name"),
                    nonnegativeLong(detail.get("order")), parentId != 0 && !departmentTree.containsKey(parentId)));
            JsonNode users = array(get("user/list", Map.of("department_id", Long.toString(departmentId)), deadline).get("userlist"));
            Set<String> departmentUsers = new HashSet<>();
            for (JsonNode user : users) {
                ensureDeadline(deadline);
                WecomMemberDTO member = member(user, departmentId);
                if (!departmentUsers.add(member.getUserId())) {
                    throw WecomRemoteFailure.invalidResponse();
                }
                observedMemberships.computeIfAbsent(member.getUserId(), ignored -> new HashSet<>()).add(departmentId);
                WecomMemberDTO existing = members.putIfAbsent(member.getUserId(), member);
                if (existing != null && !existing.equals(member)) {
                    throw WecomRemoteFailure.invalidResponse();
                }
                if (members.size() > properties.getMaxMembers()) {
                    throw new WecomRemoteFailure("LIMIT_EXCEEDED", "可见成员数量超过预览保护上限，请调整配置后重试");
                }
            }
        }
        for (WecomMemberDTO member : members.values()) {
            if (!observedMemberships.get(member.getUserId()).containsAll(
                    member.getDepartmentIds().stream().filter(departmentTree::containsKey).toList())) {
                throw WecomRemoteFailure.invalidResponse();
            }
        }
        ensureDeadline(deadline);
        return new WecomDirectoryResultDTO("PREVIEW_READY", "已完整读取本次可见组织；仅供预览，未发布目录、开户或修改权限",
                properties.getCorpId(), Instant.now(), properties.getCooldownSeconds(), List.copyOf(departments), List.copyOf(members.values()));
    }

    private void validateTree(Map<Long, JsonNode> departments) {
        for (long departmentId : departments.keySet()) {
            Set<Long> visited = new HashSet<>();
            long cursor = departmentId;
            while (departments.containsKey(cursor)) {
                if (!visited.add(cursor)) {
                    throw new WecomRemoteFailure("INVALID_RESPONSE", "企业微信部门树存在环路，本次预览未发布任何数据");
                }
                cursor = nonnegativeLong(departments.get(cursor).get("parentid"));
            }
        }
    }

    private WecomMemberDTO member(JsonNode user, long currentDepartment) {
        Set<Long> departmentIds = new LinkedHashSet<>();
        for (JsonNode departmentId : array(user.get("department"))) {
            if (!departmentIds.add(positiveLong(departmentId))) {
                throw WecomRemoteFailure.invalidResponse();
            }
        }
        if (!departmentIds.contains(currentDepartment)) {
            throw WecomRemoteFailure.invalidResponse();
        }
        JsonNode status = user.get("status");
        Integer sourceStatus = null;
        if (status != null && !status.isNull()) {
            if (!status.isIntegralNumber() || !status.canConvertToInt()) {
                throw WecomRemoteFailure.invalidResponse();
            }
            sourceStatus = status.intValue();
        }
        return new WecomMemberDTO(requiredText(user, "userid"), requiredText(user, "name"),
                optionalText(user, "position"), optionalText(user, "biz_mail"), sourceStatus,
                departmentIds.stream().sorted().toList(), directLeaders(user));
    }

    private List<String> directLeaders(JsonNode user) {
        JsonNode value = user.get("direct_leader");
        if (value == null || value.isNull()) return null;
        Set<String> leaders = new HashSet<>();
        for (JsonNode leader : array(value)) {
            if (!leader.isString() || leader.stringValue().isBlank()
                    || !leaders.add(leader.stringValue())) throw WecomRemoteFailure.invalidResponse();
        }
        return leaders.stream().sorted().toList();
    }

    private JsonNode get(String path, Map<String, String> parameters, long deadline) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = token(deadline);
            Map<String, String> authenticated = new LinkedHashMap<>(parameters);
            authenticated.put("access_token", token);
            JsonNode response = request(path, authenticated, deadline);
            int code = errorCode(response);
            if (code == 0) {
                return response;
            }
            if (attempt == 0 && (code == 40014 || code == 42001 || code == 40001)) {
                accessToken = null;
                continue;
            }
            throw WecomRemoteFailure.fromCode(code);
        }
        throw WecomRemoteFailure.invalidResponse();
    }

    private String token(long deadline) {
        if (accessToken != null && System.nanoTime() < tokenExpiresNanos) {
            return accessToken;
        }
        JsonNode response = request("gettoken", Map.of("corpid", properties.getCorpId(), "corpsecret", properties.getSecret()), deadline);
        int code = errorCode(response);
        if (code != 0) {
            throw WecomRemoteFailure.fromCode(code);
        }
        String token = requiredText(response, "access_token");
        long expiry = positiveLong(response.get("expires_in"));
        accessToken = token;
        tokenExpiresNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(0, Math.min(expiry, 7200) - 60));
        return token;
    }

    private JsonNode request(String path, Map<String, String> parameters, long deadline) {
        ensureDeadline(deadline);
        HttpUrl.Builder url = base.newBuilder().addPathSegments(path);
        parameters.forEach(url::addQueryParameter);
        Call call = http.newCall(new Request.Builder().url(url.build()).get().build());
        ensureDeadline(deadline);
        call.timeout().timeout(Math.max(1, Math.min(deadline - System.nanoTime(),
                TimeUnit.SECONDS.toNanos(properties.getRequestTimeoutSeconds()))), TimeUnit.NANOSECONDS);
        try (Response response = call.execute()) {
            if (response.code() == 429) {
                throw WecomRemoteFailure.fromCode(45009);
            }
            if (!response.isSuccessful()) {
                throw new WecomRemoteFailure("HTTP_UNAVAILABLE", "企业微信 HTTP 服务暂不可用，请稍后重试");
            }
            if (response.body() == null) {
                throw WecomRemoteFailure.invalidResponse();
            }
            byte[] bytes = response.body().byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new WecomRemoteFailure("LIMIT_EXCEEDED", "企业微信响应超过预览保护上限，本次读取已终止");
            }
            try {
                JsonNode document = json.readTree(new String(bytes, StandardCharsets.UTF_8));
                if (document == null || !document.isObject()) {
                    throw WecomRemoteFailure.invalidResponse();
                }
                return document;
            } catch (WecomRemoteFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw WecomRemoteFailure.invalidResponse();
            }
        } catch (IOException failure) {
            throw new WecomRemoteFailure("NETWORK_UNAVAILABLE", "企业微信连接失败或超时，请核实网络和出口 IP；本地业务不受影响");
        }
    }

    private int errorCode(JsonNode response) {
        JsonNode code = response.get("errcode");
        if (code == null || !code.isIntegralNumber() || !code.canConvertToInt()) {
            throw WecomRemoteFailure.invalidResponse();
        }
        return code.intValue();
    }

    private void ensureDeadline(long deadline) {
        if (System.nanoTime() >= deadline) {
            throw new WecomRemoteFailure("PREVIEW_TIMEOUT", "组织预览超过总时限，请检查网络或调整预览上限后重试");
        }
    }

    private JsonNode array(JsonNode value) {
        if (value == null || !value.isArray()) {
            throw WecomRemoteFailure.invalidResponse();
        }
        return value;
    }

    private long positiveLong(JsonNode value) {
        long number = nonnegativeLong(value);
        if (number == 0) {
            throw WecomRemoteFailure.invalidResponse();
        }
        return number;
    }

    private long nonnegativeLong(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw WecomRemoteFailure.invalidResponse();
        }
        return value.longValue();
    }

    private String requiredText(JsonNode object, String field) {
        String value = optionalText(object, field);
        if (value == null || value.isBlank()) {
            throw WecomRemoteFailure.invalidResponse();
        }
        return value;
    }

    private String optionalText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw WecomRemoteFailure.invalidResponse();
        }
        return value.stringValue();
    }

    private WecomDirectoryResultDTO configurationProblem() {
        if (!properties.isDirectoryEnabled()) {
            return result("DISABLED", "企业微信通讯录预览未启用，不影响现有招聘业务", 0);
        }
        if (properties.getCorpId() == null || properties.getCorpId().isBlank()
                || properties.getSecret() == null || properties.getSecret().isBlank()) {
            return result("NOT_CONFIGURED", "请配置企业微信企业 ID 和应用密钥后再试", 0);
        }
        return null;
    }

    private long remainingCooldown() {
        if (nextAttemptNanos == 0) {
            return 0;
        }
        return Math.max(0, (nextAttemptNanos - System.nanoTime() + TimeUnit.SECONDS.toNanos(1) - 1)
                / TimeUnit.SECONDS.toNanos(1));
    }

    private WecomDirectoryResultDTO result(String status, String message, long retryAfterSeconds) {
        return new WecomDirectoryResultDTO(status, message, properties.getCorpId(), Instant.now(),
                retryAfterSeconds, List.of(), List.of());
    }

    @PreDestroy
    public void close() {
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdown();
        accessToken = null;
    }
}
