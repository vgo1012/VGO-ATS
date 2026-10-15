package com.ezaigc.hr.talent.domain.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.enums.ResumeSummaryFactsStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.ResolvableType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;

class CandidateResumeSummarySerializationTest {
  @ParameterizedTest
  @EnumSource(ResumeSummaryFactsStatus.class)
  void writesSummaryResponse(ResumeSummaryFactsStatus status) throws Exception {
    var timestamp = Instant.parse("2026-09-11T03:14:47Z");
    var version = new ResumeVersionResponse("version", "resume", "intake", 1,
        "sample.pdf", "application/pdf", 100, "sha256", "AVAILABLE", "user", timestamp);
    var source = new ResumeSourceResponse("intake", "channel", "测试渠道", "MANUAL", "user", timestamp);
    var facts = new FactBundleResponse(
        new FactSnapshotResponse("snapshot", "version", null, null, 1, "AI_PARSED", null,
            null, null, null, "工程师", "上海", null, "UNKNOWN", null, null, null, null),
        List.of(new WorkFactResponse("2020", "至今", "work", "snapshot", "work-0", "示例公司",
            null, LocalDate.of(2020, 1, 1), null, null, null, null, 0)),
        List.of(new ProjectFactResponse(null, null, "project", "snapshot", "project-0", null,
            null, null, null, null, null, null, 0)),
        List.of(new EducationFactResponse(null, null, "education", "snapshot", "education-0",
            null, "本科", null, null, null, null)),
        List.of(new SkillFactResponse("skill", "snapshot", "skill-0", "Java", null, null)),
        List.of(new SimpleFactResponse(null, null, "simple", "snapshot", "UNKNOWN", "simple-0",
            "其他", null, null, null, null, 0)));
    var summary = new CandidateResumeSummaryResponse("candidate", version, source,
        status == ResumeSummaryFactsStatus.READY ? facts : null, status);
    var output = new MockHttpOutputMessage();
    new JacksonJsonHttpMessageConverter().write(List.of(summary),
        ResolvableType.forClassWithGenerics(List.class, CandidateResumeSummaryResponse.class),
        MediaType.APPLICATION_JSON, output, null);
    assertThat(output.getBodyAsString()).contains("\"candidateId\":\"candidate\"", "\"factsStatus\":\"" + status.name() + "\"");
  }
}
