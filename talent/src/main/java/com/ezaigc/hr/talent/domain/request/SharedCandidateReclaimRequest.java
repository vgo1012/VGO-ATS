package com.ezaigc.hr.talent.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class SharedCandidateReclaimRequest {
  @NotEmpty @Size(max = 100) private List<@NotNull @Valid SharedCandidateReclaimItemRequest> items;
}
