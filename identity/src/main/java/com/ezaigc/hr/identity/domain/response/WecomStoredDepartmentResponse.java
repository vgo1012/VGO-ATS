package com.ezaigc.hr.identity.domain.response;

import lombok.Value;

@Value
public class WecomStoredDepartmentResponse {
    String id;
    long departmentId;
    long parentId;
    String name;
    long order;
    boolean parentOutsideScope;
    String changedBatchId;
    String linkedLocalDepartmentId;
    String linkedLocalDepartmentName;
}
