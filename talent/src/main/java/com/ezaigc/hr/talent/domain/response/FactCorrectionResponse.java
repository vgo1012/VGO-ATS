package com.ezaigc.hr.talent.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 已持久化的简历事实纠正结果。 */
@Data
@AllArgsConstructor
public class FactCorrectionResponse {
  private String correctionId;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 快照版本。 */
  private int snapshotVersion;
  /** 受影响能力。 */
  private List<String> affectedCapabilities;
}
