package com.ezaigc.hr.identity.domain.response;

import java.time.Instant;
import lombok.Value;

@Value
public class WecomSyncBatchResponse {
    String id;
    String corpId;
    String triggerType;
    String actorUserId;
    String retryOfBatchId;
    String scopeVersion;
    String status;
    Instant requestedAt;
    Instant startedAt;
    Instant finishedAt;
    Instant leaseUntil;
    String errorCode;
    String message;
    int departmentCount;
    int memberCount;
    int insertedCount;
    int changedCount;
    int missingCount;
}
