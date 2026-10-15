package com.ezaigc.hr.identity.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class WecomDirectoryPreviewResponse {
    String status;
    String message;
    String corpId;
    Instant observedAt;
    long retryAfterSeconds;
    List<WecomDepartmentResponse> departments;
    List<WecomMemberResponse> members;
}
