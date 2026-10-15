package com.ezaigc.hr.integration.ai;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_PROMPT_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_PROMPT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ezaigc.hr.common.infrastructure.json.JsonNodeValues;
import com.ezaigc.hr.intelligence.matching.application.manager.AiMatchingEvaluationManager;
import com.ezaigc.hr.intelligence.matching.application.provider.ProfessionalScoringModelPort.ScoreRequest;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.service.AiProfessionalAssessmentValidator;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.recruiting.demand.service.validator.JdParseContractValidator;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeDateValue;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 显式启用的真实“JD解析→简历解析→专业评分”合成流水线评测，不启动业务容器或数据库。 */
class ProfessionalScoringParsingBenchmarkTest {

  private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 9, 20);

  @Test
  void evaluatesParsedFactsWithCurrentProfessionalScoringModel() throws IOException {
    assumeTrue(Boolean.parseBoolean(System.getenv("HR_AI_SCORING_BENCHMARK")));
    Path root = ProfessionalScoringBenchmarkTest.repositoryRoot();
    Properties configuration = ProfessionalScoringBenchmarkTest.configuration(root);
    String apiKey = requiredEnvironment("HR_AI_API_KEY");
    ObjectMapper json = JsonMapper.builder().build();
    SpringAiFactParseAdapter parser = parser(root, configuration, apiKey, json);
    var scoring = ProfessionalScoringBenchmarkTest.adapter(configuration, apiKey);
    var jdValidator = new JdParseContractValidator();
    ReflectionTestUtils.setField(jdValidator, "json", json);
    var resumeValidator = new ResumeParseContractValidator();
    ReflectionTestUtils.setField(resumeValidator, "objectMapper", json);
    ReflectionTestUtils.setField(resumeValidator, "clock", Clock.systemUTC());
    var scoreValidator = new AiProfessionalAssessmentValidator();
    ReflectionTestUtils.setField(scoreValidator, "json", json);
    Path output = outputDirectory();
    Path details = output.resolve("pipeline-results.jsonl");
    Files.writeString(details, "", StandardCharsets.UTF_8);

    List<PipelineResult> results = new ArrayList<>();
    for (Scenario scenario : List.of(strongMatch(), plannedLearning())) {
      PipelineResult result = execute(parser, scoring, jdValidator, resumeValidator, scoreValidator,
          json, scenario);
      results.add(result);
      Files.writeString(details, json.writeValueAsString(result) + System.lineSeparator(),
          StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    assertThat(results).allSatisfy(result -> {
      assertThat(result.error()).as(result.scenario()).isBlank();
      assertThat(result.professionalScore()).as(result.scenario()).isNotNull();
    });
    PipelineResult strong = results.get(0);
    assertThat(strong.jdFacts()).contains("Java", "Spring Boot");
    assertThat(strong.resumeFacts()).contains("Java", "Spring Boot", "支付");
    PipelineResult learning = results.get(1);
    assertThat(learning.resumeFacts()).contains("仓储物流", "库存盘点");
    assertThat(learning.resumeFacts()).doesNotContain("使用Java开发", "Spring Boot项目经验");
  }

  private static PipelineResult execute(SpringAiFactParseAdapter parser,
      SpringAiProfessionalScoringAdapter scoring, JdParseContractValidator jdValidator,
      ResumeParseContractValidator resumeValidator, AiProfessionalAssessmentValidator scoreValidator,
      ObjectMapper json, Scenario scenario) {
    long pipelineStarted = System.nanoTime();
    String stage = "jd-model", jdRawJson = "", jdFactsJson = "", resumeRawJson = "";
    String resumeFactsJson = "", jdInput = "", resumeInput = "", scoreRawJson = "";
    String jdModelName = "", resumeModelName = "", scoringModelName = "";
    long jdParseMillis = 0, resumeParseMillis = 0, scoringMillis = 0;
    long callStarted = 0;
    int jdInputTokens = 0, jdOutputTokens = 0, resumeInputTokens = 0, resumeOutputTokens = 0;
    int scoringInputTokens = 0, scoringOutputTokens = 0;
    try {
      long started = System.nanoTime();
      callStarted = started;
      var jdModel = parser.parse(new JdParseModelPort.ParseRequest(JD_FACT_SCHEMA_VERSION,
          JD_PARSE_PROMPT_VERSION, scenario.title(), scenario.jdText()));
      jdParseMillis = elapsedMillis(started);
      jdRawJson = jdModel.rawJson();
      jdModelName = jdModel.modelVersion();
      jdInputTokens = jdModel.inputTokens();
      jdOutputTokens = jdModel.outputTokens();
      stage = "jd-validation";
      JsonNode jdFactsNode = jdValidator.validateModel(jdModel.rawJson(), scenario.jdText());
      jdFactsJson = json.writeValueAsString(jdFactsNode);
      JdFactSnapshotData jd = jdSnapshot(scenario.id(), jdFactsNode, json);

      List<PageText> pages = List.of(new PageText(1, scenario.resumeText()));
      stage = "resume-model";
      started = System.nanoTime();
      callStarted = started;
      var resumeModel = parser.parse(new ResumeParseModelPort.ParseRequest(RESUME_FACT_SCHEMA_VERSION,
          RESUME_PARSE_PROMPT_VERSION, scenario.id() + ".txt", pages));
      resumeParseMillis = elapsedMillis(started);
      resumeRawJson = resumeModel.rawJson();
      resumeModelName = resumeModel.modelVersion();
      resumeInputTokens = resumeModel.inputTokens();
      resumeOutputTokens = resumeModel.outputTokens();
      stage = "resume-validation";
      ResumeFacts resumeFacts = resumeValidator.validateModel(resumeModel.rawJson(), pages, EVALUATED_ON);
      resumeFactsJson = json.writeValueAsString(resumeFacts);
      ResumeFactSnapshotData resume = resumeSnapshot(scenario.id(), resumeFacts);

      var manager = new AiMatchingEvaluationManager();
      ReflectionTestUtils.setField(manager, "json", json);
      jdInput = ReflectionTestUtils.invokeMethod(manager, "jdInput", jd);
      resumeInput = ReflectionTestUtils.invokeMethod(manager, "resumeInput", resume);
      stage = "scoring-model";
      started = System.nanoTime();
      callStarted = started;
      var scoreModel = scoring.score(new ScoreRequest(jdInput, resumeInput,
          AiScoringConstants.PROMPT_VERSION, AiScoringConstants.SCHEMA_VERSION, EVALUATED_ON));
      scoringMillis = elapsedMillis(started);
      scoreRawJson = scoreModel.rawJson();
      scoringModelName = scoreModel.modelVersion();
      scoringInputTokens = scoreModel.inputTokens();
      scoringOutputTokens = scoreModel.outputTokens();
      stage = "scoring-validation";
      var assessment = scoreValidator.validate(scoreModel.rawJson(), jd, resume);
      return new PipelineResult(scenario.id(), scenario.anchor(), scenario.jdText(), scenario.resumeText(),
          "completed", jdRawJson, jdFactsJson, resumeRawJson, resumeFactsJson, jdInput, resumeInput,
          scoreRawJson, assessment.getProfessionalScore(), assessment.getSummary(),
          json.writeValueAsString(assessment.getDimensions()), jdModelName, resumeModelName,
          scoringModelName, jdParseMillis, resumeParseMillis, scoringMillis,
          elapsedMillis(pipelineStarted), jdInputTokens, jdOutputTokens, resumeInputTokens,
          resumeOutputTokens, scoringInputTokens, scoringOutputTokens, "");
    } catch (RuntimeException failure) {
      if ("jd-model".equals(stage) && jdParseMillis == 0) jdParseMillis = elapsedMillis(callStarted);
      if ("resume-model".equals(stage) && resumeParseMillis == 0) {
        resumeParseMillis = elapsedMillis(callStarted);
      }
      if ("scoring-model".equals(stage) && scoringMillis == 0) {
        scoringMillis = elapsedMillis(callStarted);
      }
      return new PipelineResult(scenario.id(), scenario.anchor(), scenario.jdText(), scenario.resumeText(),
          stage, jdRawJson, jdFactsJson, resumeRawJson, resumeFactsJson, jdInput, resumeInput,
          scoreRawJson, null, "", "", jdModelName, resumeModelName, scoringModelName,
          jdParseMillis, resumeParseMillis, scoringMillis, elapsedMillis(pipelineStarted),
          jdInputTokens, jdOutputTokens, resumeInputTokens, resumeOutputTokens, scoringInputTokens,
          scoringOutputTokens, safeError(failure));
    }
  }

  private static SpringAiFactParseAdapter parser(Path root, Properties configuration, String apiKey,
      ObjectMapper json) throws IOException {
    var options = OpenAiChatOptions.builder().apiKey(apiKey)
        .baseUrl(requiredProperty(configuration, "spring.ai.openai.base-url"))
        .model(requiredProperty(configuration, "spring.ai.openai.chat.model"))
        .timeout(ProfessionalScoringBenchmarkTest.duration(
            requiredProperty(configuration, "spring.ai.openai.chat.timeout")))
        .maxRetries(Integer.parseInt(configuration.getProperty("spring.ai.openai.chat.max-retries", "0")))
        .build();
    var model = OpenAiChatModel.builder().options(options).build();
    var adapter = new SpringAiFactParseAdapter();
    var properties = new OpenAiChatProperties();
    properties.setModel(options.getModel());
    ReflectionTestUtils.setField(adapter, "clientBuilder", ChatClient.builder(model));
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ReflectionTestUtils.setField(adapter, "json", json);
    ReflectionTestUtils.setField(adapter, "resumeSchema", schema(root,
        "schema/resume-parse/1.2.json", RESUME_FACT_SCHEMA_VERSION));
    ReflectionTestUtils.setField(adapter, "jdSchema", schema(root,
        "schema/jd-parse/1.1.json", JD_FACT_SCHEMA_VERSION));
    adapter.initializeClient();
    return adapter;
  }

  private static VersionedJsonSchemaOutputConverter schema(Path root, String relative, String version)
      throws IOException {
    return new VersionedJsonSchemaOutputConverter(version,
        Files.readString(root.resolve("bootstrap/src/main/resources").resolve(relative)));
  }

  private static JdFactSnapshotData jdSnapshot(String id, JsonNode root, ObjectMapper json) {
    JsonNode professional = root.get("professional");
    JsonNode controlled = root.get("controlledConditions");
    JsonNode fulfillment = root.get("fulfillmentConditions");
    return new JdFactSnapshotData("jd-fact-" + id, "jd-version-" + id, 1,
        new JdFactSnapshotData.ProfessionalFacts(
            objects(json, professional, "coreSkills", JdFactSnapshotData.Skill.class),
            objects(json, professional, "roleExperiences", JdFactSnapshotData.Role.class),
            JsonNodeValues.decimal(professional, "minimumYears"),
            JsonNodeValues.decimal(professional, "preferredYears"),
            JsonNodeValues.strings(professional, "responsibilities"),
            JsonNodeValues.strings(professional, "industryTags"),
            JsonNodeValues.strings(professional, "projectTags")),
        new JdFactSnapshotData.ControlledFacts(
            JsonNodeValues.integer(controlled, "ageMin"), JsonNodeValues.integer(controlled, "ageMax"),
            JsonNodeValues.text(controlled, "sex"), JsonNodeValues.strings(controlled, "educations"),
            JsonNodeValues.strings(controlled, "credentials"),
            objects(json, controlled, "languages", JdFactSnapshotData.Language.class)),
        new JdFactSnapshotData.FulfillmentFacts(JsonNodeValues.strings(fulfillment, "locations"),
            JsonNodeValues.text(fulfillment, "availability"),
            JsonNodeValues.decimal(fulfillment, "salaryMin"),
            JsonNodeValues.decimal(fulfillment, "salaryMax"),
            JsonNodeValues.text(fulfillment, "salaryPeriod")));
  }

  private static <T> List<T> objects(ObjectMapper json, JsonNode node, String field, Class<T> type) {
    return JsonNodeValues.objects(json, node, field, type);
  }

  private static ResumeFactSnapshotData resumeSnapshot(String id, ResumeFacts facts) {
    var candidate = facts.candidate();
    List<ResumeFactSnapshotData.SimpleFact> simpleFacts = Stream.concat(
        facts.industryTags().stream().map(value -> new ResumeFactSnapshotData.SimpleFact(
            "INDUSTRY_TAG", "industry-" + Math.abs(value.hashCode()), value, null, null, null, null)),
        facts.responsibilityTags().stream().map(value -> new ResumeFactSnapshotData.SimpleFact(
            "RESPONSIBILITY_TAG", "responsibility-" + Math.abs(value.hashCode()), value, null, null, null, null)))
        .toList();
    return new ResumeFactSnapshotData("resume-fact-" + id, "resume-version-" + id,
        "candidate-" + id, 1,
        new ResumeFactSnapshotData.CandidateFacts(null, null, null, candidate.currentRole(),
            candidate.city(), null, null, candidate.jobIntention()),
        facts.workExperiences().stream().map(value -> new ResumeFactSnapshotData.Work(value.id(),
            value.employer(), value.role(), completeDate(value.startDate()), completeDate(value.endDate()),
            value.description(), value.responsibilities(), value.achievements(), value.startDate(),
            value.endDate())).toList(),
        facts.projectExperiences().stream().map(value -> new ResumeFactSnapshotData.Project(value.id(),
            value.projectName(), value.role(), completeDate(value.startDate()), completeDate(value.endDate()),
            value.description(), value.technologies(), value.achievements())).toList(),
        facts.educations().stream().map(value -> new ResumeFactSnapshotData.Education(value.id(),
            value.school(), value.degree(), value.major(), completeDate(value.startDate()),
            completeDate(value.endDate()), value.learningMode())).toList(),
        facts.skills().stream().map(value -> new ResumeFactSnapshotData.Skill(value.id(), value.name(),
            value.level(), value.years() == null ? null : BigDecimal.valueOf(value.years()))).toList(),
        simpleFacts);
  }

  private static Scenario strongMatch() {
    return new Scenario("strong-java-match", "人工比较：应显著高于仅计划学习场景", "高级Java后端工程师",
        "招聘高级Java后端工程师。要求5年以上Java和Spring Boot实际开发经验，负责支付系统微服务设计、性能优化和生产故障排查；有MySQL、Redis和Docker项目经验。",
        "求职方向：高级Java后端工程师。2019年至今在某科技公司任Java后端工程师，使用Java、Spring Boot、MySQL、Redis开发支付微服务，负责接口设计、性能优化与生产故障排查。2022年主导支付网关容器化，使用Docker交付，上线后接口延迟降低30%。");
  }

  private static Scenario plannedLearning() {
    return new Scenario("planned-learning-only", "人工比较：计划学习不能被当作实际Java经历", "高级Java后端工程师",
        "招聘高级Java后端工程师。要求5年以上Java和Spring Boot实际开发经验，负责支付系统微服务设计、性能优化和生产故障排查；有MySQL、Redis和Docker项目经验。",
        "求职方向：Java后端工程师。2021年至今从事仓储物流协调，负责排班、库存盘点和运输单据核对。个人学习计划：未来学习Java和Spring Boot，目前没有软件开发、支付系统、微服务或Docker项目经历。");
  }

  private static Path outputDirectory() throws IOException {
    Path output = Path.of(requiredEnvironment("HR_AI_SCORING_BENCHMARK_OUTPUT_DIR"))
        .toAbsolutePath().normalize();
    Files.createDirectories(output);
    return output;
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
    return value;
  }

  private static String requiredProperty(Properties values, String name) {
    String value = values.getProperty(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
    return value;
  }

  private static long elapsedMillis(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000);
  }

  private static LocalDate completeDate(String value) {
    ResumeDateValue parsed = ResumeDateValue.parse(value);
    return parsed == null ? null : parsed.completeDate();
  }

  private static String safeError(RuntimeException failure) {
    if (failure instanceof ResumeParseContractValidator.ContractViolation violation) {
      return violation.errorCode();
    }
    if (failure instanceof JdParseContractValidator.ContractViolation violation) {
      return violation.errorCode();
    }
    String message = failure.getMessage();
    if (message != null) {
      for (String token : message.split("[^A-Z0-9_]+")) {
        if (token.startsWith("AI_ASSESSMENT_") || token.startsWith("MODEL_SCHEMA_")) return token;
      }
    }
    String status = reflectiveStatus(failure);
    return failure.getClass().getSimpleName() + (status.isBlank() ? "" : ":HTTP_" + status);
  }

  private static String reflectiveStatus(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      for (String method : List.of("statusCode", "getStatusCode")) {
        try {
          Object value = current.getClass().getMethod(method).invoke(current);
          if (value instanceof Number number) return String.valueOf(number.intValue());
          if (value != null && value.toString().matches("[1-5][0-9]{2}")) return value.toString();
        } catch (ReflectiveOperationException ignored) {
          // 供应商异常类型不稳定；没有状态访问器时只记录异常类名。
        }
      }
    }
    return "";
  }

  private record Scenario(String id, String anchor, String title, String jdText, String resumeText) {}

  private record PipelineResult(String scenario, String anchor, String jdText, String resumeText,
                                String stage, String jdRawJson, String jdFacts,
                                String resumeRawJson, String resumeFacts, String jdScoreInput,
                                String resumeScoreInput, String scoreRawJson,
                                BigDecimal professionalScore, String summary, String dimensions,
                                String jdParseModel, String resumeParseModel, String scoringModel,
                                long jdParseMillis, long resumeParseMillis, long scoringMillis,
                                long totalMillis, int jdInputTokens, int jdOutputTokens,
                                int resumeInputTokens, int resumeOutputTokens,
                                int scoringInputTokens, int scoringOutputTokens, String error) {}
}
