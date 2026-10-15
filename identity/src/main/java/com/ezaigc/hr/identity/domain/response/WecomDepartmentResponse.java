package com.ezaigc.hr.identity.domain.response;

import lombok.Value;

@Value
public class WecomDepartmentResponse {
    long departmentId;
    long parentId;
    String name;
    long order;
    boolean parentOutsideScope;
}
