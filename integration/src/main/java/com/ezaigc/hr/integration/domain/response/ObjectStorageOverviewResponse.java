package com.ezaigc.hr.integration.domain.response;

import java.time.Instant;
import lombok.Value;

@Value
public class ObjectStorageOverviewResponse {
  String provider;
  String status;
  String region;
  String bucketDisplayName;
  String credentialMode;
  String roleName;
  boolean legacyMinioFallbackEnabled;
  Long objectCount;
  Long storageBytes;
  Long multipartUploadCount;
  Instant statisticsUpdatedAt;
  Instant sampledAt;
  String statusMessage;
}
