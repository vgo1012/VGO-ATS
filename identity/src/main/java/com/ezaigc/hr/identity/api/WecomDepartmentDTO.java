package com.ezaigc.hr.identity.api;

import lombok.Value;

@Value
public class WecomDepartmentDTO {
    long departmentId;
    long parentId;
    String name;
    long order;
    boolean parentOutsideScope;
}
