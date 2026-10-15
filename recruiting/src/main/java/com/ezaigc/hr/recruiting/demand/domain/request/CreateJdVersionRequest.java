package com.ezaigc.hr.recruiting.demand.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_JD_SOURCE_TYPE_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
public class CreateJdVersionRequest {
    /** 手工新增的明确工作区域，系统据此自动生成编号，不从正文猜测。 */
    @NotBlank(groups = NewJd.class)
    @Size(max = 80)
    private String workRegionName;

    /** 旧客户端字段；首次创建 JD 时拒绝传入，编号由岗位名称自动生成。 */
    @Size(max = 3) @Pattern(regexp = "0[1-9]|[1-9][0-9]|00[1-9]|0[1-9][0-9]|[1-9][0-9]{2}")
    private String jobTypeCode;
    /** 旧客户端字段；首次创建 JD 时拒绝传入，编号由工作地区自动生成。 */
    @Size(max = 3) @Pattern(regexp = "0[1-9]|[1-9][0-9]|00[1-9]|0[1-9][0-9]|[1-9][0-9]{2}")
    private String workRegionCode;


    /** 仅独立 JD 新增入口启用的强制归属校验组。 */
    public interface NewJd extends Default {}

    /** 业务负责人；省略时为当前用户，仅管理范围内操作者可代建。 */
    @OptionalPlatformIdentifier
    private String ownerUserId;

    /** JD 业务归属部门；新增时必填。 */
    @NotBlank(groups = NewJd.class)
    @OptionalPlatformIdentifier
    private String ownerDepartmentId;

    /** 招聘类型；新增时必填，只允许 INTERNAL 或 EXTERNAL。 */
    @NotBlank(groups = NewJd.class)
    @Size(max = 16)
    @Pattern(regexp = "INTERNAL|EXTERNAL")
    private String recruitmentType;

    @OptionalPlatformIdentifier
    private String clientId;

    /** 应用部门来源时的当前版本 ID；普通新建不填写。 */
    @OptionalPlatformIdentifier
    private String appliedFromJdVersionId;

    @Size(max = 180)
    private String distinction;

    private boolean publicVisible;
    /** 是否将当前 JD 内容公开到公司内容库；省略时不公开。 */
    private Boolean contentShared;

    /** 兼容旧客户端；独立创建 JD，不影响幂等重试。 */
    private boolean forceNew;

    /** 招聘编制。手工新增和编辑 JD 时必须明确填写。 */
    @NotNull
    @Min(1)
    @Max(10000)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer headcount;

    /** 招聘周期（天）。手工新增和编辑 JD 时必须明确填写。 */
    @NotNull
    @Min(1)
    @Max(3650)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer recruitmentCycleDays;

    /**
     * 标题。
     */
    @NotBlank
    @Size(max = 180)
    @Pattern(regexp = "(?s).{0,80}", groups = NewJd.class, message = "新增岗位名称不能超过 80 字，以便生成岗位编号")
    private String title;

    /**
     * 原始文本内容。
     */
    @NotBlank
    @Size(max = 200000)
    private String rawText;

    /**
     * 数据来源类型。
     */
    private @Size(max = MAX_JD_SOURCE_TYPE_LENGTH)
    @Pattern(regexp = INPUT_PATTERN)
    String sourceType;
}
