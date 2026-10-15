package com.ezaigc.hr.intelligence.search.application.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource.State;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Field;
import java.math.BigDecimal;

class SearchDocumentFactoryTest {
  @Test
  void emptyLatestVersionAndDeletedCandidateRetainOnlyAPositiveVersionBarrier() {
    var factory = new SearchDocumentFactory();
    var unavailable = factory.create(new State("candidate", 7, "当前姓名", "current@example.com", "13900139000",
        "version", 2, false, null, Instant.EPOCH, "UPLOAD"));
    assertThat(unavailable.getProjectionVersion()).isEqualTo(2L << 32);
    assertThat(unavailable.getCandidateVersion()).isEqualTo(7);
    assertThat(unavailable.getFactSnapshotId()).isNull();
    assertThat(unavailable.getFullName()).isEqualTo("当前姓名");
    assertThat(unavailable.getSearchText()).isEqualTo("当前姓名 current@example.com 13900139000");
    assertThat(unavailable.getSkills()).isEmpty();
    assertThat(factory.create(new State("candidate", 1, null, null, null, null, 0, false, null, Instant.EPOCH, null))
        .getProjectionVersion()).isOne();
    assertThat(factory.create(new State("candidate", 1, null, null, null, null, 0, true, null, Instant.EPOCH, null))
        .getProjectionVersion()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void mergesOverlappingEmploymentPeriodsInsteadOfDoubleCounting() {
    var facts =
        new ResumeFactSnapshotData(
            "fact",
            "resume",
            "candidate",
            1,
            new ResumeFactSnapshotData.CandidateFacts(
                "候选人", null, null, "工程师", "上海", 35, "FEMALE", "Java 后端工程师"),
            List.of(
                new ResumeFactSnapshotData.Work(
                    "w1",
                    "A",
                    "工程师",
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2022, 12, 31),
                    null,
                    List.of(),
                    List.of()),
                new ResumeFactSnapshotData.Work(
                    "w2",
                    "B",
                    "顾问",
                    LocalDate.of(2022, 1, 1),
                    LocalDate.of(2024, 12, 31),
                    null,
                    List.of(),
                    List.of())),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    var factory = new SearchDocumentFactory();
    ReflectionTestUtils.setField(
        factory, "clock", Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC));
    var document = factory.create(facts, Instant.EPOCH, null, 1);
    assertThat(document.getWorkYears())
        .isBetween(new BigDecimal("4.99"), new BigDecimal("5.01"));
    assertThat(document.getRoles()).containsExactly("工程师", "顾问");
    assertThat(document.getProjectionVersion()).isEqualTo((1L << 32) | 1);
    facts.setSnapshotVersion(Integer.MAX_VALUE);
    long oldResumeLastFact = factory.create(facts, Instant.EPOCH.plusSeconds(100), null, 1)
        .getProjectionVersion();
    facts.setSnapshotVersion(1);
    assertThat(factory.create(facts, Instant.EPOCH, null, 2).getProjectionVersion())
        .isGreaterThan(oldResumeLastFact);
    facts.setSnapshotVersion(Integer.MAX_VALUE);
    assertThat(factory.create(facts, Instant.EPOCH, null, Integer.MAX_VALUE).getProjectionVersion())
        .isPositive().isLessThanOrEqualTo(Long.MAX_VALUE);
  }

  @Test
  void usesCurrentCandidateIdentityAndContactsInSearchText() {
    var facts =
        new ResumeFactSnapshotData(
            "fact",
            "resume",
            "candidate",
            1,
            new ResumeFactSnapshotData.CandidateFacts(
                "候选人",
                "sensitive@example.com",
                "13800138000",
                "工程师",
                "上海",
                null,
                null,
                null),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    var factory = new SearchDocumentFactory();
    ReflectionTestUtils.setField(factory, "clock", Clock.systemUTC());

    var document = factory.create(new State("candidate", 7, "当前姓名", "current@example.com", "13900139000",
        "resume", 1, false, facts, Instant.EPOCH, null));

    assertThat(
            Arrays.stream(document.getClass().getDeclaredFields())
                .map(Field::getName))
        .doesNotContain("email", "phone");
    assertThat(document.getSearchText())
        .contains("当前姓名", "current@example.com", "13900139000")
        .doesNotContain("候选人", "sensitive@example.com", "13800138000");
    assertThat(document.getFullName()).isEqualTo("当前姓名");
    assertThat(document.getProjectionVersion()).isEqualTo((1L << 32) | 1);
    var updated = factory.create(new State("candidate", 8, "更新姓名", "next@example.com", "13700137000",
        "resume", 1, false, facts, Instant.ofEpochSecond(1), null));
    assertThat(updated.getProjectionVersion()).isEqualTo(document.getProjectionVersion());
    assertThat(updated.getCandidateVersion()).isGreaterThan(document.getCandidateVersion());
    assertThat(updated.getSearchText()).contains("next@example.com").doesNotContain("current@example.com");
  }
}
