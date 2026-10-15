package com.ezaigc.hr.integration.wecom;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.integration.config.WecomProperties;
import com.ezaigc.hr.recruiting.interview.api.InterviewExternalCalendarPort;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarMode;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.experimental.Accessors;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class WecomInterviewExternalCalendarAdapter implements InterviewExternalCalendarPort {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    @Resource private WecomProperties properties;
    @Resource private ObjectMapper json;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private HttpUrl base = HttpUrl.get("https://qyapi.weixin.qq.com/cgi-bin/");
    private String accessToken;
    private long tokenExpiresNanos;

    @Override
    public Result create(Command command) {
        if (!properties.getCollaboration().isEnabled()) {
            return Result.failed(false, "WECOM_CREATION_DISABLED", "企业微信日程/会议新建功能未启用");
        }
        Result configuration = configuration(false, command);
        if (configuration != null) return configuration;
        if (InterviewExternalCalendarMode.WECOM_MEETING.name().equals(command.type())) {
            return meetingCreate(command);
        }
        if (InterviewExternalCalendarMode.WECOM_SCHEDULE.name().equals(command.type())) {
            return scheduleCreate(command);
        }
        return Result.failed(false, "WECOM_TYPE_INVALID", "不支持的企业微信外部对象类型");
    }

    @Override
    public Result update(Command command) {
        Result configuration = configuration(true, command);
        if (configuration != null) return configuration;
        if (InterviewExternalCalendarMode.WECOM_MEETING.name().equals(command.type())) {
            ObjectNode payload = meetingPayload(command);
            payload.put("meetingid", command.externalObjectId());
            PostResult response = post("meeting/update", payload);
            Result updated = result(response, command.externalObjectId(), null, null,
                    remoteStatus(response.document(), "ACTIVE"), false);
            return updated.succeeded() ? meetingDetails(command.externalObjectId(), updated) : updated;
        }
        if (InterviewExternalCalendarMode.WECOM_SCHEDULE.name().equals(command.type())) {
            ObjectNode payload = json.createObjectNode();
            payload.put("skip_attendees", 0);
            ObjectNode schedule = schedulePayload(command);
            schedule.put("schedule_id", command.externalObjectId());
            payload.set("schedule", schedule);
            PostResult response = post("oa/schedule/update", payload);
            return result(response, command.externalObjectId(), null, null, "ACTIVE", false);
        }
        return Result.failed(false, "WECOM_TYPE_INVALID", "不支持的企业微信外部对象类型");
    }

    @Override
    public Result cancel(Command command) {
        Result configuration = configuration(true, command);
        if (configuration != null) return configuration;
        ObjectNode payload = json.createObjectNode();
        String path;
        if (InterviewExternalCalendarMode.WECOM_MEETING.name().equals(command.type())) {
            path = "meeting/cancel";
            payload.put("meetingid", command.externalObjectId());
        } else if (InterviewExternalCalendarMode.WECOM_SCHEDULE.name().equals(command.type())) {
            path = "oa/schedule/del";
            payload.put("schedule_id", command.externalObjectId());
        } else {
            return Result.failed(false, "WECOM_TYPE_INVALID", "不支持的企业微信外部对象类型");
        }
        PostResult response = post(path, payload);
        return result(response, command.externalObjectId(), null, null, "CANCELLED", false);
    }

    private Result meetingCreate(Command command) {
        PostResult response = post("meeting/create", meetingPayload(command));
        String id = optionalText(response.document(), "meetingid");
        Result created = result(response, id, optionalText(response.document(), "meeting_code"),
                optionalText(response.document(), "meeting_link"), remoteStatus(response.document(), "ACTIVE"), true);
        return created.succeeded() ? meetingDetails(id, created) : created;
    }

    private Result meetingDetails(String meetingId, Result fallback) {
        try {
            ObjectNode payload = json.createObjectNode().put("meetingid", meetingId);
            PostResult response = post("meeting/get_info", payload);
            if (!response.confirmed() || errorCode(response.document()) != 0) return fallback;
            return result(response, meetingId, optionalText(response.document(), "meeting_code"),
                    optionalText(response.document(), "meeting_link"), remoteStatus(response.document(), fallback.remoteStatus()), false);
        } catch (RuntimeException failure) {
            // 补充会议号/链接失败不能丢弃已确认的创建 ID。
            return fallback;
        }
    }

    private Result scheduleCreate(Command command) {
        ObjectNode payload = json.createObjectNode();
        payload.set("schedule", schedulePayload(command));
        payload.put("agentid", properties.getCollaboration().getAgentId());
        PostResult response = post("oa/schedule/add", payload);
        return result(response, optionalText(response.document(), "schedule_id"), null, null, "ACTIVE", true);
    }

    private ObjectNode meetingPayload(Command command) {
        ObjectNode payload = json.createObjectNode();
        payload.put("admin_userid", command.adminUserId());
        payload.put("title", command.title());
        payload.put("meeting_start", command.startAt().getEpochSecond());
        payload.put("meeting_duration", Duration.between(command.startAt(), command.endAt()).toSeconds());
        put(payload, "description", command.description());
        put(payload, "location", command.location());
        var invitees = payload.putObject("invitees").putArray("userid");
        command.inviteeUserIds().forEach(invitees::add);
        ObjectNode settings = payload.putObject("settings");
        settings.put("enable_waiting_room", command.waitingRoomEnabled());
        settings.put("allow_enter_before_host", command.allowEnterBeforeHost());
        settings.put("enable_enter_mute", command.enterMuteEnabled() ? 1 : 0);
        payload.putObject("reminders").putArray("remind_before").add(command.reminderSeconds());
        put(payload, "cal_id", properties.getCollaboration().getCalendarId());
        return payload;
    }

    private ObjectNode schedulePayload(Command command) {
        ObjectNode schedule = json.createObjectNode();
        schedule.putArray("admins").add(command.adminUserId());
        schedule.put("start_time", command.startAt().getEpochSecond());
        schedule.put("end_time", command.endAt().getEpochSecond());
        schedule.put("is_whole_day", 0);
        var attendees = schedule.putArray("attendees");
        command.inviteeUserIds().forEach(userId -> attendees.addObject().put("userid", userId));
        schedule.put("summary", command.title());
        put(schedule, "description", command.description());
        put(schedule, "location", command.location());
        ObjectNode reminders = schedule.putObject("reminders");
        reminders.put("is_remind", 1);
        reminders.put("remind_before_event_secs", command.reminderSeconds());
        reminders.put("is_repeat", 0);
        put(schedule, "cal_id", properties.getCollaboration().getCalendarId());
        return schedule;
    }

    private Result configuration(boolean requiresExternalId, Command command) {
        if (CommonUtils.isBlank(properties.getCorpId()) || CommonUtils.isBlank(properties.getSecret())) {
            return Result.failed(false, "WECOM_NOT_CONFIGURED", "企业微信企业 ID 或应用密钥未配置");
        }
        if (requiresExternalId && CommonUtils.isBlank(command.externalObjectId())) {
            return Result.failed(false, "WECOM_EXTERNAL_ID_MISSING", "企业微信外部对象 ID 缺失");
        }
        if (InterviewExternalCalendarMode.WECOM_SCHEDULE.name().equals(command.type())
                && properties.getCollaboration().getAgentId() <= 0 && !requiresExternalId) {
            return Result.failed(false, "WECOM_AGENT_ID_REQUIRED", "创建企业微信普通日程需要配置自建应用 agentId");
        }
        return null;
    }

    private PostResult post(String path, ObjectNode payload) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Request request;
            try {
                String token = token();
                HttpUrl url = base.newBuilder().addPathSegments(path)
                        .addQueryParameter("access_token", token).build();
                request = new Request.Builder().url(url)
                        .post(RequestBody.create(json.writeValueAsString(payload), JSON)).build();
            } catch (RuntimeException failure) {
                // 此分支尚未执行创建 POST，可以确认本次未创建。
                return new PostResult(errorDocument(503, "request not sent"), true);
            }
            try (Response response = execute(request)) {
                if (!response.isSuccessful()) return new PostResult(errorDocument(503, "http unavailable"), false);
                JsonNode document = read(response);
                int code = errorCode(document);
                boolean confirmed = validErrorCode(document);
                if (confirmed && attempt == 0 && (code == 40001 || code == 40014 || code == 42001)) {
                    synchronized (this) {
                        accessToken = null;
                    }
                    continue;
                }
                return new PostResult(document, confirmed);
            } catch (IOException | RuntimeException failure) {
                return new PostResult(errorDocument(503, "network response unconfirmed"), false);
            }
        }
        return new PostResult(errorDocument(40014, "credentials rejected"), true);
    }

    private synchronized String token() {
        if (accessToken != null && System.nanoTime() < tokenExpiresNanos) return accessToken;
        HttpUrl url = base.newBuilder().addPathSegment("gettoken")
                .addQueryParameter("corpid", properties.getCorpId())
                .addQueryParameter("corpsecret", properties.getSecret()).build();
        try (Response response = execute(new Request.Builder().url(url).get().build())) {
            JsonNode document = read(response);
            int code = errorCode(document);
            if (!response.isSuccessful() || code != 0) {
                throw new IllegalStateException("enterprise wechat token rejected");
            }
            accessToken = requiredText(document, "access_token");
            long expires = document.path("expires_in").longValue();
            tokenExpiresNanos = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(Math.max(0, Math.min(expires, 7200) - 60));
            return accessToken;
        } catch (IOException failure) {
            throw new IllegalStateException("enterprise wechat token unavailable", failure);
        }
    }

    private Response execute(Request request) throws IOException {
        var call = http.newCall(request);
        call.timeout().timeout(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
        return call.execute();
    }

    private JsonNode read(Response response) throws IOException {
        if (response.body() == null) throw new IOException("empty response");
        byte[] bytes = response.body().byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("response too large");
        try {
            JsonNode document = json.readTree(new String(bytes, StandardCharsets.UTF_8));
            if (document == null || !document.isObject()) throw new IOException("invalid response");
            return document;
        } catch (RuntimeException failure) {
            throw new IOException("invalid response", failure);
        }
    }

    private Result result(PostResult received, String id, String code, String link,
            String remoteStatus, boolean requireId) {
        if (requireId && !received.confirmed()) {
            return Result.unknownCreate("WECOM_CREATE_RESULT_UNKNOWN", "创建请求可能已生效，请核对原请求及远端结果");
        }
        JsonNode response = received.document();
        int errorCode = errorCode(response);
        if (errorCode != 0) {
            return Result.failed(retryable(errorCode), "WECOM_" + errorCode,
                    safeRemoteMessage(errorCode));
        }
        if (requireId && CommonUtils.isBlank(id)) {
            return Result.unknownCreate("WECOM_INVALID_RESPONSE", "企业微信成功响应缺少外部对象 ID");
        }
        return new Result(true, false, id, code, link, remoteStatus, null, null);
    }

    private static boolean retryable(int code) {
        return code == 503 || code == 45009 || code == 45011 || code == 40014 || code == 42001;
    }

    private static String safeRemoteMessage(int code) {
        return switch (code) {
            case 60020 -> "服务器出口 IP 未加入企业微信可信 IP";
            case 48002, 60011, 60001 -> "企业微信应用缺少日程、会议权限或成员可见范围";
            case 45009, 45011 -> "企业微信调用频率受限";
            case 40001, 40013, 40014, 42001 -> "企业微信凭据无效或已过期";
            case 503 -> "企业微信网络服务暂不可用";
            default -> "企业微信拒绝本次日程或会议操作（错误码 " + code + "）";
        };
    }

    private static String remoteStatus(JsonNode response, String normal) {
        JsonNode excluded = response == null ? null : response.get("excess_users");
        return excluded != null && excluded.isArray() && !excluded.isEmpty()
                ? normal + "_WITH_EXCLUDED_USERS" : normal;
    }

    private ObjectNode errorDocument(int code, String message) {
        return json.createObjectNode().put("errcode", code).put("errmsg", message);
    }

    private static int errorCode(JsonNode response) {
        JsonNode value = response == null ? null : response.get("errcode");
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                ? value.intValue() : 503;
    }

    private static boolean validErrorCode(JsonNode response) {
        JsonNode value = response == null ? null : response.get("errcode");
        return value != null && value.isIntegralNumber() && value.canConvertToInt();
    }

    @Value
    @Accessors(fluent = true)
    private static class PostResult {
        JsonNode document;
        boolean confirmed;
    }

    private static String optionalText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isString() && !value.stringValue().isBlank()
                ? value.stringValue() : null;
    }

    private static String requiredText(JsonNode object, String field) {
        String value = optionalText(object, field);
        if (value == null) throw new IllegalStateException("missing enterprise wechat field");
        return value;
    }

    private static void put(ObjectNode target, String field, String value) {
        if (!CommonUtils.isBlank(value)) target.put(field, value);
    }

    @PreDestroy
    public void close() {
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdown();
        accessToken = null;
    }
}
