package com.ezaigc.hr.intelligence.matching.domain.request;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 批量退回采用全有或全无事务。 */
@Data
public class ScreeningReturnRequest {
  private @NotEmpty @Size(max = MAX_MATCH_RUN_RESUMES)
      List<@NotNull @Valid ScreeningReturnItemRequest> items;
}
