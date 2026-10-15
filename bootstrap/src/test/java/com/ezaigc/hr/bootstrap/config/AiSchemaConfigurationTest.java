package com.ezaigc.hr.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.integration.ai.VersionedJsonSchemaOutputConverter;
import com.ezaigc.hr.integration.config.AiSchemaConfiguration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class AiSchemaConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(AiSchemaConfiguration.class);

  @Test
  void exposesDistinctVersionedSchemaBeansWhenAiParsingIsEnabled() {
    contextRunner
        .withPropertyValues("hr.ai.parsing.enabled=true")
        .run(
            context -> {
              VersionedJsonSchemaOutputConverter resume =
                  context.getBean(
                      "resumeParseJsonSchema", VersionedJsonSchemaOutputConverter.class);
              VersionedJsonSchemaOutputConverter jd =
                  context.getBean("jdParseJsonSchema", VersionedJsonSchemaOutputConverter.class);
              assertThat(resume.version()).isEqualTo("1.2");
              assertThat(resume.getJsonSchema()).contains("/schema/resume-parse/1.2.json");
              assertThat(jd.version()).isEqualTo("1.1");
              assertThat(jd.getJsonSchema()).contains("/schema/jd-parse/1.1.json");
            });
  }

  @Test
  void parsingSchemasContainFactsWithoutEvidenceOrReliability() {
    contextRunner.withPropertyValues("hr.ai.parsing.enabled=true").run(context -> {
      var mapper = JsonMapper.builder().build();
      var resume = mapper.readTree(context.getBean(
          "resumeParseJsonSchema", VersionedJsonSchemaOutputConverter.class).getJsonSchema());
      assertThat(resume.path("properties").has("evidence")).isFalse();
      assertThat(resume.at("/$defs/skill/properties/id").isMissingNode()).isFalse();

      var jd = mapper.readTree(context.getBean(
          "jdParseJsonSchema", VersionedJsonSchemaOutputConverter.class).getJsonSchema());
      assertThat(jd.path("properties").has("evidence")).isFalse();
      assertThat(jd.path("properties").has("confidence")).isFalse();
      assertThat(jd.path("properties").has("reliability")).isFalse();
      assertThat(jd.at("/$defs/skill/properties/id").isMissingNode()).isFalse();
    });
  }

  @Test
  void birthDateSchemasPreservePrecisionAndExcludePresentOrInventedPrecision() {
    contextRunner.withPropertyValues("hr.ai.parsing.enabled=true").run(context -> {
      var mapper = JsonMapper.builder().build();
      var resume = mapper.readTree(context.getBean(
          "resumeParseJsonSchema", VersionedJsonSchemaOutputConverter.class).getJsonSchema());
      var canonicalDate = resume.at("/properties/candidate/properties/birthDate");
      assertThat(canonicalDate.path("type").toString()).isEqualTo("[\"string\",\"null\"]");
      String pattern = canonicalDate.path("pattern").asString();
      for (String date : new String[] {"1998", "1998-05", "1998-05-12"}) {
        assertThat(Pattern.matches(pattern, date)).as(date).isTrue();
      }
      for (String date : new String[] {"PRESENT", "1998-00", "1998-13", "1998-05-00",
          "1998-05-32", "1998年5月", "98-05", ""}) {
        assertThat(Pattern.matches(pattern, date)).as(date).isFalse();
      }
    });
  }

  @Test
  void doesNotLoadSchemaResourcesWhileAiParsingIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(VersionedJsonSchemaOutputConverter.class);
        });
  }
}
