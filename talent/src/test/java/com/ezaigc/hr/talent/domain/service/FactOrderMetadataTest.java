package com.ezaigc.hr.talent.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FactOrderMetadataTest {
  @Test
  void parsesEachFactOnceWhenRestoringAShuffledArray() {
    var rows = IntStream.range(0, 128).mapToObj(i -> new CountingFact(i * 37 % 128)).toList();
    var restored = FactOrderMetadata.restore(rows);

    assertEquals(128, rows.stream().mapToInt(row -> row.reads).sum());
    for (int i = 0; i < 128; i++) assertEquals(i, restored.get(i).sourceIndex);
    assertEquals(37, rows.get(1).sourceIndex);
    assertThrows(UnsupportedOperationException.class, () -> restored.add(new CountingFact(128)));
  }

  @Test
  void duplicateIndicesStayStableAndLegacyMetadataShortCircuits() {
    var first = new CountingFact(2);
    var second = new CountingFact(2);
    var earlier = new CountingFact(0);
    var result = FactOrderMetadata.restore(List.of(first, earlier, second, first));
    assertSame(earlier, result.get(0));
    assertSame(first, result.get(1));
    assertSame(second, result.get(2));
    assertSame(first, result.get(3));

    var legacy = new CountingFact(1);
    legacy.setExt("{}");
    var malformed = new CountingFact(3);
    malformed.setExt("not-json");
    var rows = List.of(earlier, legacy, malformed);
    assertSame(rows, FactOrderMetadata.restore(rows));
    assertEquals(0, malformed.reads);
  }

  private static final class CountingFact extends StandardPersistenceFields {
    private final int sourceIndex;
    private int reads;

    private CountingFact(int sourceIndex) {
      this.sourceIndex = sourceIndex;
      setExt("{\"sourceIndex\":" + sourceIndex + "}");
    }

    @Override
    public String getExt() {
      reads++;
      return super.getExt();
    }
  }

  @Test
  void restoresOriginalEvidenceOrderAndPreservesDatePrecision() {
    var first = new WorkFact();
    first.setExt("{\"startDateText\":\"2022-03\"}");
    var second = new WorkFact();
    FactOrderMetadata.capture(List.of(first, second));
    assertEquals("2022-03", FactDateMetadata.read(first.getExt(), "startDateText", null));
    assertEquals(0, FactOrderMetadata.index(first.getExt()));
    assertEquals(1, FactOrderMetadata.index(second.getExt()));
    assertEquals(List.of(first, second), FactOrderMetadata.restore(List.of(second, first)));
  }

  @Test
  void doesNotGuessLegacyOrderOrShareIndicesAcrossArrays() {
    var first = new WorkFact(); first.setEmployer("a");
    var second = new WorkFact(); second.setEmployer("b");
    var third = new WorkFact(); third.setEmployer("a");
    var rows = List.of(first, second, third);
    assertSame(rows, FactOrderMetadata.restore(rows));
    assertNull(FactOrderMetadata.index(first.getExt()));
    FactOrderMetadata.capture(rows, WorkFact::getEmployer);
    assertEquals(0, FactOrderMetadata.index(second.getExt()));
    assertEquals(1, FactOrderMetadata.index(third.getExt()));
  }
}
