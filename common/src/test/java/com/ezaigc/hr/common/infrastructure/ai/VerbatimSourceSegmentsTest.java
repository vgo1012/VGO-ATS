package com.ezaigc.hr.common.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class VerbatimSourceSegmentsTest {
  @Test
  void referencesIdentifyExactPageSlicesWithoutChangingWhitespaceOrSurrogatePairs() {
    String source = " 技能 Java \r\n\n" + "中".repeat(799) + "📄GPU";
    var segments = VerbatimSourceSegments.from(2, source);
    assertThat(segments).hasSize(3);
    assertThat(segments.getFirst().sourceId()).isEqualTo("p2:0");
    assertThat(segments.getFirst().text()).isEqualTo(" 技能 Java ");
    for (var segment : segments) {
      assertThat(segment.text()).isEqualTo(source.substring(segment.startOffset(), segment.endOffset()));
      assertThat(Character.isHighSurrogate(segment.text().charAt(segment.text().length() - 1))).isFalse();
    }
  }
}
