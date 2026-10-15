package com.ezaigc.hr.talent.domain.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class CorrectionPersistenceResultDTO {
  private final String correctionId;
  /** 事实快照 ID。 */
  private final String factSnapshotId;
  /** 快照版本。 */
  private final int snapshotVersion;
  /** 受影响能力。 */
  private final List<String> affectedCapabilities;

  public CorrectionPersistenceResultDTO(
      String correctionId,
      String factSnapshotId,
      int snapshotVersion,
      List<String> affectedCapabilities) {
    this.correctionId = correctionId;
    this.factSnapshotId = factSnapshotId;
    this.snapshotVersion = snapshotVersion;
    this.affectedCapabilities = List.copyOf(affectedCapabilities);
  }
}
