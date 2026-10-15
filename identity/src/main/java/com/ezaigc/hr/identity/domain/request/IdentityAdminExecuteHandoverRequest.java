package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.identity.constant.IdentityConstants.HANDOVER_RESOURCE_KEY_INPUT_PATTERN;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_HANDOVER_RESOURCE_KEY_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_HANDOVER_SELECTED_RESOURCES;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_HANDOVER_SELECTION_MODE_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_REASON_LENGTH;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminExecuteHandoverRequest {

    /**
     * 目标用户 ID。
     */
    private @PlatformIdentifier String targetUserId;
    /**
     * 选择模式。
     */
    private @NotBlank
            @Size(max = MAX_HANDOVER_SELECTION_MODE_LENGTH)
            @Pattern(regexp = INPUT_PATTERN) String selectionMode;
    /**
     * 已选择资源键列表。
     */
    private @Size(max = MAX_HANDOVER_SELECTED_RESOURCES)
            Set<
                    @NotBlank
                    @Size(max = MAX_HANDOVER_RESOURCE_KEY_LENGTH)
                    @Pattern(regexp = HANDOVER_RESOURCE_KEY_INPUT_PATTERN) String>
            selectedResourceKeys;
    /**
     * 操作原因。
     */
    private @NotBlank @Size(max = MAX_REASON_LENGTH) String reason;

}
