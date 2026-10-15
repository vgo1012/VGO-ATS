package com.ezaigc.hr.reporting.controller;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.reporting.application.manager.AuditApplicationManager;
import com.ezaigc.hr.reporting.domain.response.AuditPageResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    /** 聚合审计目标可能来自外部系统，长度与持久化字段保持一致。 */
    private static final int MAX_AUDIT_TARGET_IDENTIFIER_LENGTH = 120;

    @Resource
    private AuditApplicationManager manager;

    @GetMapping
    AuditPageResponse search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @OptionalFilterCode @RequestParam(required = false) String eventType,
            @OptionalPlatformIdentifier @RequestParam(required = false) String actorUserId,
            @OptionalFilterCode @RequestParam(required = false) String targetType,
            @RequestParam(required = false)
            @Size(max = MAX_AUDIT_TARGET_IDENTIFIER_LENGTH, message = "targetId 长度不能超过 {max}")
            String targetId,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(defaultValue = "20") int limit) {
        return manager.search(from, to, eventType, actorUserId, targetType, targetId, cursor, limit);
    }
}
