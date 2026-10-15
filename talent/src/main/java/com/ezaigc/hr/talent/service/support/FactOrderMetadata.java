package com.ezaigc.hr.talent.service.support;

import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 保存模型事实数组顺序，防止按日期或名称排序后证据指针指向另一条事实。 */
public final class FactOrderMetadata {
  private static final ObjectMapper JSON = new ObjectMapper();
  private FactOrderMetadata() {}
  public static <T extends StandardPersistenceFields> List<T> capture(List<T> rows) {
    return capture(rows, ignored -> "facts");
  }
  public static <T extends StandardPersistenceFields> List<T> capture(List<T> rows, Function<T,String> group) {
    var next = new HashMap<String,Integer>();
    for (T row : rows) {
      int index = next.merge(group.apply(row), 1, Integer::sum) - 1;
      ObjectNode ext = row.getExt() == null || row.getExt().isBlank() ? JSON.createObjectNode() : (ObjectNode) JSON.readTree(row.getExt());
      ext.put("sourceIndex", index);
      row.setExt(ext.toString());
    }
    return rows;
  }
  public static Integer index(String ext) {
    if (ext == null || ext.isBlank()) return null;
    var value = JSON.readTree(ext).get("sourceIndex");
    return value != null && value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 0 ? value.asInt() : null;
  }
  public static <T extends StandardPersistenceFields> List<T> restore(List<T> rows) {
    // 历史快照没有顺序元数据时不推测原始下标；其现有展示顺序保持稳定。
    var indices = new IdentityHashMap<T, Integer>();
    for (T row : rows) {
      Integer sourceIndex = index(row.getExt());
      if (sourceIndex == null) return rows;
      indices.put(row, sourceIndex);
    }
    // 比较时只读已解析下标，避免反复解析JSON或计算实体的equals/hashCode。
    return rows.stream().sorted(Comparator.comparingInt(indices::get)).toList();
  }
}
