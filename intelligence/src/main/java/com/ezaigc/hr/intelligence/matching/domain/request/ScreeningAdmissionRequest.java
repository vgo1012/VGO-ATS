package com.ezaigc.hr.intelligence.matching.domain.request;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 批量加入智能筛选，单次最多一千条。 */
@Data
public class ScreeningAdmissionRequest {
  private @NotEmpty @Size(max = MAX_MATCH_RUN_RESUMES) List<@NotNull @Valid ScreeningAdmissionItemRequest> items;
}
