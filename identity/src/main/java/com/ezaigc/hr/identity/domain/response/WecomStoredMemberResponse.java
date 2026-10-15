package com.ezaigc.hr.identity.domain.response;

import java.util.List;
import lombok.Value;

@Value
public class WecomStoredMemberResponse {
    String id;
    String userId;
    String name;
    String position;
    String businessEmail;
    Integer sourceStatus;
    List<Long> departmentIds;
    List<String> directLeaderUserIds;
    String directoryStatus;
    String changedBatchId;
    String linkedUserAccountId;
    String linkedUserDisplayName;
    String linkedUserWorkEmail;
}
