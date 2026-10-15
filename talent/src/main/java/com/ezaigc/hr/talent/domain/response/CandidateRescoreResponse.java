package com.ezaigc.hr.talent.domain.response;

import java.util.List;
import lombok.Value;

@Value
public class CandidateRescoreResponse {
  String operationId;
  int requestedCount;
  List<CandidateRescoreItemResponse> items;
}
