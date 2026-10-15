package com.ezaigc.hr.intelligence.search.domain.response;

import java.time.Instant;
import lombok.Value;

@Value
public class SearchIndexResourceResponse {
  String id;
  String indexName;
  String status;
  boolean managed;
  String version;
  String reconciliationReason;
  String reconciledBy;
  Instant reconciledAt;
}
