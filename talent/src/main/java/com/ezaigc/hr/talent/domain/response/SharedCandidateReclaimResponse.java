package com.ezaigc.hr.talent.domain.response;

import java.util.List;
import lombok.Value;

@Value
public class SharedCandidateReclaimResponse {
  int reclaimedCount;
  List<String> candidateIds;
}
