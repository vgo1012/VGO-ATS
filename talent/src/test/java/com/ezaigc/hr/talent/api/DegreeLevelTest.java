package com.ezaigc.hr.talent.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.api.enums.DegreeLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class DegreeLevelTest {

  @Test
  void normalizesCommonPostgraduateAndUndergraduateAliases() {
    assertThat(DegreeLevel.canonicalOrOriginal("硕士研究生")).isEqualTo("硕士");
    assertThat(DegreeLevel.canonicalOrOriginal("Master's Degree")).isEqualTo("硕士");
    assertThat(DegreeLevel.canonicalOrOriginal("大学本科")).isEqualTo("本科");
    assertThat(DegreeLevel.rankOf("硕士研究生")).isGreaterThan(DegreeLevel.rankOf("本科"));
  }

  @Test
  void expandsCanonicalSearchSelectionToStoredAliases() {
    assertThat(DegreeQueryAliases.expand(List.of("硕士")))
        .contains("硕士", "硕士研究生", "Master Degree", "MASTER DEGREE");
  }
}
