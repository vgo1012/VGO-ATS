package com.ezaigc.hr.identity.domain.response;

import java.util.List;
import lombok.Value;

@Value
public class WecomStoredDirectoryResponse {
    String corpId;
    String publishedBatchId;
    List<WecomStoredDepartmentResponse> departments;
    List<WecomStoredMemberResponse> members;
}
