package com.ezaigc.hr.talent.api.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 跨解析、筛选与展示边界使用的学历层级归一规则。 */
public enum DegreeLevel implements CodeDescEnum {
  DOCTORATE("博士", 8, List.of("博士", "博士研究生", "博士及以上", "doctor", "doctorate", "phd", "PhD", "Ph.D.")),
  MASTER("硕士", 7, List.of("硕士", "硕士研究生", "研究生", "硕士及以上", "master", "Master Degree", "Master's Degree")),
  BACHELOR("本科", 6, List.of("本科", "大学本科", "学士", "本科及以上", "bachelor", "Bachelor Degree", "Bachelor's Degree")),
  ASSOCIATE("大专", 5, List.of("专科", "大专", "大学专科", "associate", "Associate Degree", "college")),
  HIGH_SCHOOL("高中", 4, List.of("高中", "普通高中", "High School", "HIGH_SCHOOL")),
  SECONDARY("中专", 3, List.of("中专", "中等专科", "technicalsecondaryschool")),
  MIDDLE_SCHOOL("初中", 2, List.of("初中", "middleschool", "juniorhighschool")),
  PRIMARY_SCHOOL("小学", 1, List.of("小学", "primaryschool", "elementaryschool"));

  private final String desc;
  private final int rank;
  private final List<String> aliases;

  DegreeLevel(String desc, int rank, List<String> aliases) {
    this.desc = desc;
    this.rank = rank;
    this.aliases = List.copyOf(aliases);
  }

  @Override
  public String getDesc() {
    return desc;
  }

  public String label() {
    return desc;
  }

  public int rank() {
    return rank;
  }

  public List<String> aliases() {
    return aliases;
  }

  public static Optional<DegreeLevel> fromText(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    String normalized = normalize(value);
    for (DegreeLevel level : values()) {
      if (level.aliases.stream().map(DegreeLevel::normalize).anyMatch(normalized::equals)) {
        return Optional.of(level);
      }
    }
    return Optional.empty();
  }

  public static int rankOf(String value) {
    return fromText(value).map(DegreeLevel::rank).orElse(0);
  }

  public static String canonicalOrOriginal(String value) {
    if (value == null || value.isBlank()) return value;
    return fromText(value).map(DegreeLevel::label).orElse(value.strip());
  }

  private static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s.'’_-]+", "");
  }
}
