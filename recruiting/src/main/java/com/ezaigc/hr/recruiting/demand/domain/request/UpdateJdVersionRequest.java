package com.ezaigc.hr.recruiting.demand.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_JD_SOURCE_TYPE_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

/** 更新 JD 内容；归属字段可省略并保留现值，所属人仅允许超级管理员变更。 */
@Data
public class UpdateJdVersionRequest {
  /** 工作分类可调整，已生成编号保持不变；省略则保留。 */
  @Size(max = 3) @Pattern(regexp = "0[1-9]|[1-9][0-9]|00[1-9]|0[1-9][0-9]|[1-9][0-9]{2}") private String jobTypeCode;
  @Size(max = 3) @Pattern(regexp = "0[1-9]|[1-9][0-9]|00[1-9]|0[1-9][0-9]|[1-9][0-9]{2}") private String workRegionCode;

  @Size(max = 80) private String workRegionName;

  @OptionalPlatformIdentifier private String ownerUserId;
  @OptionalPlatformIdentifier private String ownerDepartmentId;
  @Size(max = 16) @Pattern(regexp = "INTERNAL|EXTERNAL") private String recruitmentType;
  @OptionalPlatformIdentifier private String clientId;
  @Size(max = 180) private String distinction;
  private boolean publicVisible;
  /** 省略时保留当前共享状态。 */
  private Boolean contentShared;
  /** 兼容旧客户端；独立创建 JD，不影响幂等重试。 */
  private boolean forceNew;
  @NotNull @Min(1) @Max(10000)
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer headcount;
  @NotNull @Min(1) @Max(3650)
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer recruitmentCycleDays;
  @NotBlank @Size(max = 180) private String title;
  @NotBlank @Size(max = 200000) private String rawText;
  @Size(max = MAX_JD_SOURCE_TYPE_LENGTH) @Pattern(regexp = INPUT_PATTERN)
  private String sourceType;
  @ValidResourceVersion private String version;
}
