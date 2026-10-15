package com.ezaigc.hr.recruiting.demand.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_COLLABORATORS_PER_DEMAND;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_RECRUITMENT_TYPE_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class UpdateDemandRequest {

    /**
     * 标题。
     */
    @NotBlank
    @Size(max = 180)
    private String title;

    /**
     * 客户 ID。
     */
    @PlatformIdentifier
    private String clientId;
    /**
     * 项目 ID。
     */
    @OptionalPlatformIdentifier
    private String projectId;
    /**
     * 职位 ID。
     */
    @PlatformIdentifier
    private String positionId;
    /**
     * 招聘类型。
     */
    @NotBlank
    @Size(max = MAX_RECRUITMENT_TYPE_LENGTH)
    @Pattern(regexp = INPUT_PATTERN)
    private String recruitmentType;

    /**
     * 计划招聘人数。
     */
    @Min(1)
    @Max(10000)
    private int headcount;

    /**
     * 描述。
     */
    @Size(max = 10000)
    private String description;

    /**
     * 业务负责人用户 ID。
     */
    @OptionalPlatformIdentifier
    private String ownerUserId;
    /**
     * 业务归属部门 ID。
     */
    @OptionalPlatformIdentifier
    private String ownerDepartmentId;

    /** 未提供或为 null 时保留现有协作人，显式空列表表示清空。 */
    @Size(max = MAX_COLLABORATORS_PER_DEMAND)
    private List<@PlatformIdentifier String> collaboratorUserIds;

    /**
     * 归属原因。
     */
    @Size(max = 500)
    private String ownershipReason;

    /**
     * 技术乐观锁版本号。
     */
    @ValidResourceVersion
    private String version;
}
