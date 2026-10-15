package com.ezaigc.hr.integration.application.provider;

import com.ezaigc.hr.integration.domain.dto.ObjectStorageSnapshotDTO;

public interface ObjectStorageAdministrationPort {
  ObjectStorageSnapshotDTO snapshot();

  String bucketDisplayName();
}
