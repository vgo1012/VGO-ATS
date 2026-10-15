package com.ezaigc.hr.identity.domain.response;

import java.util.List;
import lombok.Value;

@Value
public class WecomMemberResponse {
    String userId;
    String name;
    String position;
    String businessEmail;
    Integer sourceStatus;
    List<Long> departmentIds;
    List<String> directLeaderUserIds;
}
