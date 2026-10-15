package com.ezaigc.hr.identity.api;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class WecomDirectoryResultDTO {
    String status;
    String message;
    String corpId;
    Instant observedAt;
    long retryAfterSeconds;
    List<WecomDepartmentDTO> departments;
    List<WecomMemberDTO> members;
}
