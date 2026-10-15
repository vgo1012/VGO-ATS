package com.ezaigc.hr.integration.domain.dto;

import java.time.Instant;
import lombok.Value;

@Value
public class ObjectStorageSnapshotDTO {
  boolean available;
  Long objectCount;
  Long storageBytes;
  Long multipartUploadCount;
  Instant statisticsUpdatedAt;
  Instant sampledAt;
}
