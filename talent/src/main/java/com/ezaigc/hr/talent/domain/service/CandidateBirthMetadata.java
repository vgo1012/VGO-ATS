package com.ezaigc.hr.talent.domain.service;

import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import java.time.LocalDate;
import java.util.LinkedHashMap;

/** 出生信息保留原始日期精度，年龄日期标明事实快照的时间边界。 */
public final class CandidateBirthMetadata {
  private CandidateBirthMetadata() {}

  public static void capture(FactSnapshot snapshot, String birthDate, LocalDate ageAsOf) {
    snapshot.setExt(write(birthDate, ageAsOf));
  }

  public static String write(String birthDate, LocalDate ageAsOf) {
    var metadata = new LinkedHashMap<String, String>();
    metadata.put("birthDate", birthDate);
    metadata.put("ageAsOf", ageAsOf == null ? null : ageAsOf.toString());
    return JsonText.write(metadata);
  }

  public static String birthDate(FactSnapshot snapshot) {
    return FactDateMetadata.read(snapshot.getExt(), "birthDate", null);
  }

  public static LocalDate ageAsOf(FactSnapshot snapshot) {
    String value = FactDateMetadata.read(snapshot.getExt(), "ageAsOf", null);
    return value == null ? null : LocalDate.parse(value);
  }
}
