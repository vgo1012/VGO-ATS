package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_DEPARTMENT_NAME_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminCreateDepartmentRequest {

    /**
     * 名称。
     */
    private @NotBlank @Size(max = MAX_DEPARTMENT_NAME_LENGTH) String name;
    /**
     * 上级部门 ID。
     */
    private @OptionalPlatformIdentifier String parentDepartmentId;

}
