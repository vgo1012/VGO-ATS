package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.talent.application.assembler.ResumeParsingAssembler;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.domain.entity.FactBundle;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.service.CandidateBirthMetadata;
import com.ezaigc.hr.talent.mapper.ResumeParsingMapper;
import com.ezaigc.hr.talent.service.impl.ResumeParsingPersistenceServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateBirthPersistenceTest {
  private static final LocalDate AS_OF = LocalDate.of(2026, 9, 11);

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "1997-05-04")
  void correctionKeepsBirthForOldClientsAndNeverOverridesTheirExplicitAge(String proposedBirth) {
    var previous = new CandidateIdentity("张三", null, null, null, "上海", 30, null, null, "1996-03");
    var proposed = new CandidateIdentity("张三", null, null, null, "杭州", 28, null, null, proposedBirth);
    CandidateIdentity normalized = ReflectionTestUtils.invokeMethod(
        ResumeParsingApplicationManager.class, "preserveBirthDate", proposed, previous);
    assertThat(normalized.birthDate()).isEqualTo(proposedBirth == null ? "1996-03" : proposedBirth);
    assertThat(normalized.city()).isEqualTo("杭州");
    assertThat(normalized.age()).isEqualTo(28);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1996", "1996-03", "1996-03-14"})
  void persistsAndReturnsOriginalBirthPrecisionWithoutReplacingExplicitAge(String birthDate) {
    var mapper = mock(ResumeParsingMapper.class);
    var service = new ResumeParsingPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);

    service.insertFactSnapshot(
        "snapshot", "version", "parse", null, 1, "AI_PARSE", "张三", null, null,
        null, null, 28, null, null, CandidateBirthMetadata.write(birthDate, AS_OF), null, null,
        Instant.parse("2026-09-11T00:00:00Z"));

    var captor = ArgumentCaptor.forClass(FactSnapshot.class);
    verify(mapper).insert(captor.capture());
    FactSnapshot stored = captor.getValue();
    assertThat(CandidateBirthMetadata.birthDate(stored)).isEqualTo(birthDate);
    assertThat(CandidateBirthMetadata.ageAsOf(stored)).isEqualTo(AS_OF);
    var response = new ResumeParsingAssembler().toResponse(bundle(stored)).getSnapshot();
    assertThat(response.getBirthDate()).isEqualTo(birthDate);
    assertThat(response.getAgeAsOf()).isEqualTo(AS_OF);
    assertThat(response.getAge()).isEqualTo(28);

    ResumeFacts restored = ReflectionTestUtils.invokeMethod(
        new ResumeParsingApplicationManager(), "toFacts", bundle(stored));
    assertThat(restored.candidate().birthDate()).isEqualTo(birthDate);
    assertThat(restored.candidate().age()).isEqualTo(28);
  }

  @Test
  void historicalSnapshotWithoutBirthMetadataRemainsReadableAndUnknown() {
    var historical = new FactSnapshot();
    historical.setAge(28);
    historical.setCreateTime(Instant.parse("2025-01-01T00:00:00Z"));
    var response = new ResumeParsingAssembler().toResponse(bundle(historical)).getSnapshot();
    assertThat(response.getAge()).isEqualTo(28);
    assertThat(response.getBirthDate()).isNull();
    assertThat(response.getAgeAsOf()).isNull();
  }

  private static FactBundle bundle(FactSnapshot snapshot) {
    return new FactBundle(snapshot, List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
