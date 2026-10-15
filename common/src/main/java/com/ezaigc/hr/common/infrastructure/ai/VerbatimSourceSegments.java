package com.ezaigc.hr.common.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import lombok.ToString;
import lombok.experimental.Accessors;

/** 为不可变原文片段编号；引用仅在同一次抽取的页内解析，不搜索或改写文字。 */
public final class VerbatimSourceSegments {
  private static final int MAX_SEGMENT_LENGTH = 800;
  private VerbatimSourceSegments() {}

  public static List<Segment> from(int pageNo, String source) {
    if (pageNo < 1 || source == null) throw new IllegalArgumentException("invalid source page");
    var result = new ArrayList<Segment>();
    int start = 0;
    while (start < source.length()) {
      int newline = source.indexOf('\n', start);
      int end = newline < 0 ? source.length() : newline;
      if (end > start && source.charAt(end - 1) == '\r') end--;
      while (start < end) {
        int chunkEnd = Math.min(end, start + MAX_SEGMENT_LENGTH);
        if (chunkEnd < end && Character.isHighSurrogate(source.charAt(chunkEnd - 1))) chunkEnd--;
        String text = source.substring(start, chunkEnd);
        if (!text.isBlank()) result.add(new Segment("p" + pageNo + ":" + start, pageNo, start, chunkEnd, text));
        start = chunkEnd;
      }
      start = newline < 0 ? source.length() : newline + 1;
    }
    return List.copyOf(result);
  }

  @Value
  @Accessors(fluent = true)
  public static class Segment {
    String sourceId;
    int pageNo;
    int startOffset;
    int endOffset;
    @ToString.Exclude String text;
  }
}
