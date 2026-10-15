package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CandidateRescoreRequest {
  @PlatformIdentifier private String jdVersionId;
  @NotEmpty @Size(max = 100) private List<@NotNull @Valid CandidateRescoreItemRequest> items;
}
