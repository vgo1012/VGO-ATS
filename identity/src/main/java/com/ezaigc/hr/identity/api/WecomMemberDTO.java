package com.ezaigc.hr.identity.api;

import java.util.List;
import lombok.Value;
import lombok.AllArgsConstructor;

@Value
@AllArgsConstructor
public class WecomMemberDTO {
    String userId;
    String name;
    String position;
    String businessEmail;
    Integer sourceStatus;
    List<Long> departmentIds;
    List<String> directLeaderUserIds;

    public WecomMemberDTO(String userId, String name, String position, String businessEmail,
                          Integer sourceStatus, List<Long> departmentIds) {
        this(userId, name, position, businessEmail, sourceStatus, departmentIds, null);
    }
}
