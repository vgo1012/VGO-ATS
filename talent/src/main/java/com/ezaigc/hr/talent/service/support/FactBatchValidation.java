package com.ezaigc.hr.talent.service.support;

import java.util.List;
import java.util.Objects;

/** 简历事实及其持久化明细共用的批次边界校验。 */
public final class FactBatchValidation {
  private FactBatchValidation() {}

  public static void validateBatch(List<?> facts, int maximumSize, String label) {
    if (facts == null
        || facts.size() > maximumSize
        || facts.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " batch is invalid");
    }
  }
}
