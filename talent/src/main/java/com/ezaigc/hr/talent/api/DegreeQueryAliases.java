package com.ezaigc.hr.talent.api;

import com.ezaigc.hr.talent.api.enums.DegreeLevel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** 查询侧学历等价词：兼容历史 keyword 索引，保留原始事实，不推断模糊文凭层级。 */
public final class DegreeQueryAliases {
  private DegreeQueryAliases() {}

  public static List<String> expand(List<String> selected) {
    var expanded = new LinkedHashSet<String>();
    for (String value : selected) {
      expanded.addAll(DegreeLevel.fromText(value)
          .map(level -> variants(level.aliases().toArray(String[]::new)))
          .orElse(List.of(value)));
    }
    return List.copyOf(expanded);
  }

  private static List<String> variants(String... values) {
    return Stream.of(values).flatMap(value -> Stream.of(value,
        value.toUpperCase(Locale.ROOT), value.toLowerCase(Locale.ROOT),
        value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT)))
        .distinct().toList();
  }
}
