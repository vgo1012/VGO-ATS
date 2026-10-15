package com.ezaigc.hr.identity.domain.response;

import lombok.Value;

@Value
public class WecomCollaborationMemberResponse {
    String userId;
    String name;
    String position;
    String linkedUserAccountId;
}
