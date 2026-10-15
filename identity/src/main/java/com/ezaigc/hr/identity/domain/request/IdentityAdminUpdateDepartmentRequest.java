package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_DEPARTMENT_NAME_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_STATUS_CODE_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminUpdateDepartmentRequest {

    /**
     * 名称。
     */
    private @NotBlank @Size(max = MAX_DEPARTMENT_NAME_LENGTH) String name;
    /**
     * 上级部门 ID。
     */
    private @OptionalPlatformIdentifier String parentDepartmentId;
    /**
     * 部门分配队列负责人用户 ID。
     */
    private @OptionalPlatformIdentifier String assignmentQueueOwnerUserId;
    /**
     * 当前状态。
     */
    private @NotBlank @Size(max = MAX_STATUS_CODE_LENGTH) @Pattern(regexp = INPUT_PATTERN)
            String status;
    /**
     * 技术乐观锁版本号。
     */
    private @ValidResourceVersion String version;

}
