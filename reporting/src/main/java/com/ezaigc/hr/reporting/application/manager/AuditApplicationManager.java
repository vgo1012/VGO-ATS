package com.ezaigc.hr.reporting.application.manager;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.reporting.application.assembler.ReportingAssembler;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import com.ezaigc.hr.reporting.domain.response.AuditPageResponse;
import com.ezaigc.hr.reporting.enums.AuditCaptureLane;
import com.ezaigc.hr.reporting.enums.AuditCaptureSource;
import com.ezaigc.hr.reporting.service.AuditCaptureService;
import com.ezaigc.hr.reporting.service.AuditService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计应用服务，负责用例编排、权限校验和事务边界。
 */
@Component
public class AuditApplicationManager {

    @Resource
    private AuthorizationService authorization;
    @Resource
    private Clock clock;
    @Resource
    private AuditService service;
    @Resource
    private AuditCaptureService captureService;
    @Resource
    private ReportingAssembler assembler;
    /**
     * 不透明游标签名编解码器。
     */
    @Resource
    private CursorCodec cursorCodec;

    /** 在单个短事务中采集一个来源通道的下一批事件。 */
    @Transactional
    public int captureNext(AuditCaptureSource source, AuditCaptureLane lane, int limit) {
        return captureService.captureNext(source, lane, limit);
    }

    @Transactional(readOnly = true)
    public AuditPageResponse search(
            Instant from,
            Instant to,
            String eventType,
            String actorUserId,
            String targetType,
            String targetId,
            String cursor,
            int limit) {
        authorization.requirePermission("audit.read-authorized");
        var actor = authorization.currentActor();
        String normalizedEventType = CommonUtils.stripToNull(eventType);
        String normalizedActorUserId = CommonUtils.stripToNull(actorUserId);
        String normalizedTargetType = CommonUtils.stripToNull(targetType);
        String normalizedTargetId = CommonUtils.stripToNull(targetId);
        String scope =
                CursorScopes.scope(
                        "reporting.audit",
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getManagedUserIds(),
                        List.of(),
                        normalizedEventType,
                        normalizedActorUserId,
                        normalizedTargetType,
                        normalizedTargetId);
        AuditPageCursor parsedCursor = parse(cursor, scope);
        Instant end =
                parsedCursor == null
                        ? (to == null ? clock.instant().plus(1, ChronoUnit.MILLIS) : to)
                        : parsedCursor.end();
        Instant start =
                parsedCursor == null
                        ? (from == null ? end.minus(30, ChronoUnit.DAYS) : from)
                        : parsedCursor.start();
        if (parsedCursor != null
                && ((from != null && !from.equals(start)) || (to != null && !to.equals(end)))) {
            throw PlatformExceptions.badRequest("AUDIT_CURSOR_INVALID", "审计游标与时间范围不匹配");
        }
        if (!start.isBefore(end)) {
            throw PlatformExceptions.badRequest("AUDIT_TIME_RANGE_INVALID", "审计开始时间必须早于结束时间");
        }
        int boundedLimit = PageLimits.resolve(limit);
        var rows =
                service.search(
                        start,
                        end,
                        normalizedEventType,
                        normalizedActorUserId,
                        normalizedTargetType,
                        normalizedTargetId,
                        parsedCursor == null ? null : parsedCursor.at(),
                        parsedCursor == null ? null : parsedCursor.id(),
                        actor.hasCompanyWideScope(),
                        actor.getUserId(),
                        List.copyOf(actor.getManagedUserIds()),
                        List.of(),
                        boundedLimit + 1);
        boolean hasMore = rows.size() > boundedLimit;
        var page = hasMore ? rows.subList(0, boundedLimit) : rows;
        String nextCursor = hasMore ? encode(scope, start, end, page.getLast()) : null;
        return new AuditPageResponse(
                assembler.toAuditResponses(page), nextCursor, hasMore, clock.instant());
    }

    private String encode(String scope, Instant start, Instant end, AuditEventEntity event) {
        return cursorCodec.encode(
                scope,
                List.of(start.toString(), end.toString(), event.getOccurredAt().toString(), event.getId()));
    }

    private AuditPageCursor parse(String raw, String scope) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            List<String> parts = cursorCodec.decode(raw, scope, 4);
            if (parts.get(3).length() != 26) {
                throw new IllegalArgumentException("incomplete cursor");
            }
            return new AuditPageCursor(
                    Instant.parse(parts.get(0)),
                    Instant.parse(parts.get(1)),
                    Instant.parse(parts.get(2)),
                    parts.get(3));
        } catch (RuntimeException exception) {
            throw PlatformExceptions.badRequest("AUDIT_CURSOR_INVALID", "审计游标无效");
        }
    }

    @Getter(AccessLevel.PRIVATE)
    @Accessors(fluent = true)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class AuditPageCursor {

        private final Instant start;
        private final Instant end;
        private final Instant at;
        private final String id;
    }
}
