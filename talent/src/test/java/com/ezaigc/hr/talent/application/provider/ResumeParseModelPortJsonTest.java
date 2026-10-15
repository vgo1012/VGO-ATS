package com.ezaigc.hr.talent.application.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ResumeParseModelPortJsonTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void resumeFactsRetainNamedPropertiesAcrossJsonRoundTrip() {
    ResumeFacts facts =
        new ResumeFacts(
            new CandidateIdentity(
                "合成候选人",
                "candidate@example.com",
                "13800000000",
                "工程师",
                "上海",
                31,
                "FEMALE",
                "Java 工程师"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("客户支持"),
            List.of());

    var tree = json.valueToTree(facts);
    ResumeFacts restored = json.treeToValue(tree, ResumeFacts.class);

    assertThat(tree.path("candidate").path("currentRole").asString()).isEqualTo("工程师");
    assertThat(restored.candidate().currentRole()).isEqualTo("工程师");
    assertThat(restored.responsibilityTags()).containsExactly("客户支持");
  }
}
