package com.ezaigc.hr.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.infrastructure.ai.AiQuotaExceededException;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import com.openai.core.http.Headers;
import com.openai.errors.PermissionDeniedException;
import com.openai.models.ErrorObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class SpringAiFactParseAdapterTest {

  @Test
  void exposesStableConfiguredIdentityForPreCallCaching() {
    var identity = adapter(new CapturingChatModel()).cacheIdentity();

    assertThat(identity.provider()).isEqualTo("spring-ai-openai");
    assertThat(identity.modelVersion()).isEqualTo("fallback-model");
  }

  @Test
  void translatesQuotaExhaustionWithoutConfusingOrdinaryPermissionErrors() {
    var model = new CapturingChatModel();
    var adapter = adapter(model);
    model.failure = PermissionDeniedException.builder().headers(Headers.builder().build()).error(ErrorObject.builder()
        .code("insufficient_quota").message("provider-only details").param(Optional.empty())
        .type("insufficient_quota").build()).build();
    assertThatThrownBy(() -> adapter.parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
        List.of(new ResumeParseModelPort.PageText(1, "张三")))))
        .isInstanceOf(AiQuotaExceededException.class).hasMessage(AiQuotaExceededException.USER_MESSAGE);
    model.failure = PermissionDeniedException.builder().headers(Headers.builder().build()).error(ErrorObject.builder()
        .code("access_denied").message("not quota").param(Optional.empty())
        .type("permission").build()).build();
    assertThatThrownBy(() -> adapter.parse(new JdParseModelPort.ParseRequest("1.1", "prompt-1", "JD", "Java")))
        .isSameAs(model.failure);
  }

  private static final String RESUME_SCHEMA =
      """
      {
        "type": "object",
        "additionalProperties": false,
        "properties": {"resume": {"type": "boolean"}},
        "required": ["resume"]
      }
      """;
  private static final String JD_SCHEMA =
      """
      {
        "type": "object",
        "additionalProperties": false,
        "properties": {"jd": {"type": "boolean"}},
        "required": ["jd"]
      }
      """;
  @Test
  void serializesDisabledThinkingAtProviderRequestRoot() throws Exception {
    var body = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = """
          {"id":"test","object":"chat.completion","created":1,"model":"qwen-test",
           "choices":[{"index":0,"message":{"role":"assistant","content":"{\"resume\":true}"},"finish_reason":"stop"}],
           "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
          """.replace("{\"resume\":true}", "{\\\"resume\\\":true}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      try (var output = exchange.getResponseBody()) { output.write(response); }
    });
    server.start();
    try {
      var model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
          .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
          .apiKey("synthetic-test-key").model("qwen3.8-flash").maxRetries(0).build()).build();
      var adapter = adapter(model);
      ReflectionTestUtils.setField(adapter, "configuredModel", "qwen3.8-flash");
      adapter.parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
          List.of(new ResumeParseModelPort.PageText(1, "Java experience"))));
      var request = JsonMapper.builder().build().readTree(body.get());
      assertThat(request.path("enable_thinking").isBoolean()).isTrue();
      assertThat(request.path("enable_thinking").asBoolean()).isFalse();
      assertThat(request.has("extra_body")).isFalse();
      assertThat(request.path("response_format").path("type").asString()).isEqualTo("json_schema");
      assertThat(request.path("response_format").path("json_schema").isObject()).isTrue();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void disablesThinkingForConfiguredQwenModel() {
    var model = new CapturingChatModel();
    var adapter = adapter(model);
    ReflectionTestUtils.setField(adapter, "configuredModel", "qwen3.8-flash");
    adapter.parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
        List.of(new ResumeParseModelPort.PageText(1, "Java experience"))));
    assertThat(((OpenAiChatOptions) model.onlyPrompt().getOptions()).getExtraBody())
        .containsEntry("enable_thinking", false);
    assertThat(((OpenAiChatOptions) model.onlyPrompt().getOptions()).getTemperature())
        .isEqualTo(0.0);
  }

  @Test
  void instructsQwenToExtractAtomicCompleteJdScoringFacts() {
    var model = new CapturingChatModel();

    adapter(model).parse(new JdParseModelPort.ParseRequest(
        "1.1", "prompt-1", "AI 工程师", "精通 Python、RAG，熟悉 Docker 优先"));

    assertThat(model.onlyPrompt().getSystemMessage().getText()).contains(
        "完整但克制", "原子事实", "required=true", "明确的优先、加分或熟悉项",
        "Python", "RAG", "Docker", "roleExperiences", "minimumYears",
        "responsibilities", "industryTags", "projectTags", "不输出 evidence",
        "可靠性", "不得达到 maxItems 上限");
  }

  @Test
  void keepsJdResponsibilitiesInTheirOriginalBusinessContext() {
    var model = new CapturingChatModel();
    adapter(model).parse(new JdParseModelPort.ParseRequest(
        "1.1", "prompt-1", "资产管理专员",
        "岗位职责：负责资产相关运营流程的规划设计、开发需求文档撰写、工具平台的落地推动。任职要求：良好的文档撰写能力。"));

    assertThat(model.onlyPrompt().getSystemMessage().getText()).contains(
        "不要过度解读 JD",
        "保留原文的业务对象、限定条件和上下文",
        "不得把任职要求中的通用能力改写成岗位职责",
        "不能据此推断软件开发职责或编程技能",
        "有歧义时保留原文表述，不自行补全含义");
  }

  @Test
  void preservesAlternativeSkillsWithoutInventingMandatoryGates() {
    var model = new CapturingChatModel();
    adapter(model).parse(new JdParseModelPort.ParseRequest(
        "1.1", "prompt-1", "AI 工程师", "掌握 PyTorch 或 TensorFlow 任意一种，熟悉 RAG"));

    assertThat(model.onlyPrompt().getSystemMessage().getText()).contains(
        "required 只区分主要技能与优先技能，不代表淘汰条件",
        "不得仅因“熟悉、掌握、精通”或“任职要求”标题升级为必须",
        "任选技术仍逐项保留原子事实",
        "不得把“和、均须”改成任选",
        "不输出 evidence、来源编号、JSON Pointer");
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .doesNotContain("或该技术是岗位主要工作内容且没有优先措辞时 required=true");
  }

  @Test
  void normalizesEquivalentResponsibilitiesConsistentlyWithDirectEvidence() {
    var resumeModel = new CapturingChatModel();
    adapter(resumeModel).parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
        List.of(new ResumeParseModelPort.PageText(1, "完成 RAG 问答开发和模型服务部署"))));
    var jdModel = new CapturingChatModel();
    adapter(jdModel).parse(new JdParseModelPort.ParseRequest(
        "1.1", "prompt-1", "AI 工程师", "负责 RAG 问答与模型部署"));

    for (var model : List.of(resumeModel, jdModel)) {
      assertThat(model.onlyPrompt().getSystemMessage().getText()).contains(
          "职责仅在原文直接支持且含义等价时归一化",
          "“RAG 问答开发”与“RAG 问答”统一为“RAG 问答”",
          "“模型服务部署”与“模型部署”统一为“模型部署”",
          "不得把普通服务部署归一化为模型部署",
          "不得新增原文未支持的技术、上位技能、熟练程度或职责",
          "对象必须具体且保持范围",
          "配电设备监测",
          "巡检不等于维修",
          "JD 未写的年限、行业或技能仍保持缺失",
          "不能为了五维都有分而补造要求或经历",
          "后续由独立 AI 评分阶段同时对照两侧冻结事实判断");
    }
    assertThat(resumeModel.onlyPrompt().getSystemMessage().getText()).contains(
        "pages 数组就是系统已从简历文件提取出的正文",
        "每个 pages[].text 都是必须逐页读取的简历正文",
        "缺少姓名、雇主名称、目标岗位或目标技术经历，不等于正文缺失",
        "仓储、物流、排班、库存盘点",
        "未来计划只能作为原文语境",
        "不能进入 skills、workExperiences、projectExperiences");
  }

  @Test
  void sendsFileNameAsUntrustedContextWithoutAddingEvidenceMetadata() {
    var model = new CapturingChatModel();
    var adapter = adapter(model);
    var request = new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
        "C:\\private\\候选人_Java\"工程师.pdf", List.of(new ResumeParseModelPort.PageText(1, "Java experience")));
    adapter.parse(request);
    String user = model.onlyPrompt().getUserMessage().getText();
    var payload = JsonMapper.builder().build().readTree(user.substring(
        "<untrusted-resume>\n".length(), user.indexOf("\n</untrusted-resume>")));
    assertThat(payload.path("fileName").asString()).isEqualTo("候选人_Java\"工程师.pdf");
    assertThat(payload.path("pages").get(0).path("pageNo").asInt()).isEqualTo(1);
    assertThat(payload.path("pages").get(0).path("text").asString()).isEqualTo("Java experience");
    assertThat(payload.toString()).doesNotContain("sourceNo", "evidence");
    assertThat(user).doesNotContain("private");
    assertThat(request.toString()).doesNotContain("候选人", "Java experience");
    assertThat(model.onlyPrompt().getSystemMessage().getText()).contains("文件名不能作为事实依据", "与正文冲突时以正文为准");
  }

  @Test
  void doesNotSendQwenExtensionToOtherProviders() {
    assertThat(FastExtractionOptions.forModel("other-model").build().getExtraBody()).isNullOrEmpty();
    assertThat(FastExtractionOptions.supportsNativeJsonSchema("other-model")).isFalse();
    assertThat(FastExtractionOptions.supportsNativeJsonSchema("qwen3.8-flash")).isTrue();
  }

  @Test
  void sendsCompactResumeFactSchemaAndKeepsResponseMetadata() {
    CapturingChatModel model = new CapturingChatModel();
    SpringAiFactParseAdapter adapter = adapter(model);

    ResumeParseModelPort.ModelResult result =
        adapter.parse(
            new ResumeParseModelPort.ParseRequest(
                "1.0", "prompt-1", List.of(new ResumeParseModelPort.PageText(1, "张三"))));

    assertThat(result.rawJson()).isEqualTo("{\"resume\":true}");
    assertThat(result.modelVersion()).isEqualTo("qwen-test");
    assertThat(result.inputTokens()).isEqualTo(12);
    assertThat(result.outputTokens()).isEqualTo(8);
    assertThat(model.onlyPrompt().getOptions()).isInstanceOfSatisfying(
        OpenAiChatOptions.class,
        options -> assertThat(options.getResponseFormat().getType())
            .isEqualTo(ResponseFormat.Type.JSON_OBJECT));
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .contains("\"resume\": {\"type\": \"boolean\"}");
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .contains("不输出 evidence、来源编号、JSON Pointer", "数组条目 id 使用同类英文前缀",
            "不能因缺少日而丢弃年月", "未知结束时间填 null", "PRESENT", "原文出现结束早于开始");
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .contains("逐项保留明确出现的具体技术名称", "不能只保留小节标题", "日期集中在页首",
            "不得按段落顺序猜配", "不因缺少年龄、性别", "文档自身的职业事实冲突",
            "硕士研究生", "不得因同时存在本科经历而把最高学历降级");
  }

  @Test
  void instructsModelToExtractBirthDatePrecisionWithoutGuessingAge() {
    var model = new CapturingChatModel();
    adapter(model).parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
        List.of(new ResumeParseModelPort.PageText(1, "张三，1998年5月出生"))));

    assertThat(model.onlyPrompt().getSystemMessage().getText()).contains(
        "candidate.birthDate", "YYYY、YYYY-MM 或 YYYY-MM-DD", "不能补造月份或生日",
        "只有出生信息时 age 返回 null", "年龄：25", "不得提取岗位年龄要求或他人年龄",
        "模型不得计算年龄", "不得从年龄反推出生日期",
        "不得根据毕业时间、教育阶段、工作年限、身份证号码或文件名推测",
        "只有出生信息时 age 返回 null");
    assertThat(model.onlyPrompt().getUserMessage().getText()).contains("1998年5月出生");
  }

  @Test
  void sendsPageTextDirectlyAndPreservesRawResponse() {
    CapturingChatModel model = new CapturingChatModel();
    SpringAiFactParseAdapter adapter = adapter(model);

    var result = adapter.parse(new ResumeParseModelPort.ParseRequest(
        "1.0", "resume-facts-v7", List.of(new ResumeParseModelPort.PageText(2, "张三\nLinux"))));

    assertThat(result.rawJson()).isEqualTo(model.resumeResponse);
    assertThat(model.onlyPrompt().getUserMessage().getText())
        .contains("\"pageNo\":2", "张三\\nLinux")
        .doesNotContain("sourceNo", "startOffset", "endOffset", "quote");
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .contains("不输出 evidence、来源编号、JSON Pointer", "只提取正文明确表达的事实")
        .doesNotContain("sourceId", "jsonPointer", "sourceNo");
  }

  @Test
  void keepsQwenJdOnDirectFactSchemaWithoutEvidenceMetadata() {
    var model = new CapturingChatModel();
    var adapter = adapter(model);
    ReflectionTestUtils.setField(adapter, "configuredModel", "qwen3.8-flash");

    adapter.parse(new JdParseModelPort.ParseRequest("1.1", "prompt-1", "Java 工程师", "Java"));

    Prompt prompt = model.onlyPrompt();
    assertThat(prompt.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
      assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
      assertThat(options.getResponseFormat().getJsonSchema()).isNull();
    });
    assertThat(prompt.getSystemMessage().getText())
        .contains("只返回一个满足 JSON Schema 的 JSON 对象", "\"jd\": {\"type\": \"boolean\"}",
            "不输出 evidence、来源编号、JSON Pointer");
    assertThat(prompt.getUserMessage().getText())
        .contains("\"title\":\"Java 工程师\"", "\"text\":\"Java\"")
        .doesNotContain("sourceNo", "startOffset", "endOffset", "evidence");
  }

  @Test
  void constrainsSupportedQwenResumeWithNativeSchemaWithoutClientSideReparse() {
    var model = new CapturingChatModel();
    model.resumeResponse = """
        {"candidate":{"fullName":"张三","email":null,"phone":null,"currentRole":null,
          "city":null,"birthDate":null,"age":null,"gender":null,"jobIntention":null},
         "workExperiences":[],"projectExperiences":[],"educations":[],"skills":[],
         "credentials":[],"languages":[],"industryTags":[],"responsibilityTags":[]}
        """;
    var adapter = adapter(model);
    ReflectionTestUtils.setField(adapter, "configuredModel", "qwen3.8-flash");
    ReflectionTestUtils.setField(adapter, "resumeSchema", new VersionedJsonSchemaOutputConverter(
        "1.0", """
            {"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"test",
             "type":"object","properties":{"candidate":{"type":"object"},
             "unknownItems":{"type":"array"}},"required":["candidate","unknownItems"]}
            """));

    var result = adapter.parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
        List.of(new ResumeParseModelPort.PageText(1, "张三"))));

    assertThat(result.rawJson()).isEqualTo(model.resumeResponse);
    assertThat(model.prompts).hasSize(1);
    var validator = new ResumeParseContractValidator();
    ReflectionTestUtils.setField(validator, "objectMapper", JsonMapper.builder().build());
    assertThat(validator.validateModel(result.rawJson(),
        List.of(new ResumeParseModelPort.PageText(1, "张三")), LocalDate.of(2026, 9, 30))
        .candidate().fullName()).isEqualTo("张三");
    assertThat(model.onlyPrompt().getOptions()).isInstanceOfSatisfying(
        OpenAiChatOptions.class,
        options -> {
          assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
          var schema = JsonMapper.builder().build().readTree(options.getResponseFormat().getJsonSchema());
          assertThat(schema.has("$schema")).isFalse();
          assertThat(schema.has("$id")).isFalse();
          assertThat(schema.path("properties").has("candidate")).isTrue();
          assertThat(schema.path("properties").has("unknownItems")).isFalse();
          assertThat(schema.path("required").size()).isEqualTo(1);
          assertThat(schema.path("required").get(0).asString()).isEqualTo("candidate");
        });
  }

  @Test
  void rejectsTruncatedResumeOutputBeforeBusinessValidation() {
    var model = new CapturingChatModel();
    model.finishReason = "length";
    var adapter = adapter(model);

    assertThatThrownBy(() -> adapter.parse(new ResumeParseModelPort.ParseRequest(
        "1.0", "prompt-1", List.of(new ResumeParseModelPort.PageText(1, "张三")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("model structured output exceeded token limit");
  }

  @Test
  void selectsJdSchemaAndEscapesUntrustedTitleAttribute() {
    CapturingChatModel model = new CapturingChatModel();
    SpringAiFactParseAdapter adapter = adapter(model);

    JdParseModelPort.ModelResult result =
        adapter.parse(new JdParseModelPort.ParseRequest("1.1", "prompt-1", "A\"<B", "Java"));

    assertThat(result.rawJson()).isEqualTo("{\"jd\":true}");
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .contains("\"jd\": {\"type\": \"boolean\"}",
            "title 属性仅是岗位名称元数据", "title 必须返回 null");
    assertThat(model.onlyPrompt().getUserMessage().getText()).contains("\"title\":\"A\\\"<B\"");
    assertThat(model.onlyPrompt().getUserMessage().getText())
        .contains("\"text\":\"Java\"")
        .doesNotContain("pageNo", "sourceNo", "startOffset", "endOffset", "boundingBox");
    assertThat(model.onlyPrompt().getSystemMessage().getText())
        .doesNotContain("/candidate/", "/skills/");
  }

  @Test
  void rejectsUnknownSchemaVersionBeforeCallingTheModel() {
    CapturingChatModel model = new CapturingChatModel();
    SpringAiFactParseAdapter adapter = adapter(model);

    assertThatThrownBy(
            () ->
                adapter.parse(new JdParseModelPort.ParseRequest("2.0", "prompt-1", "Java", "Java")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unsupported AI schema version: 2.0");
    assertThat(model.prompts).isEmpty();
  }

  @Test
  void sanitizesFileNameBeforeSendingResumePrompt() {
    String[][] cases = {
        {null, ""}, {"", ""}, {"C:\\private\\candidate.pdf", "candidate.pdf"},
        {"/private/简历.pdf", "简历.pdf"}, {"/private/", ""},
        {"C:\\private/mixed\\resume.pdf", "resume.pdf"},
        {"简历\n\u200D.pdf", "简历  .pdf"}, {" resume.pdf ", " resume.pdf "},
        {"a".repeat(256), "a".repeat(255)},
        {"a".repeat(254) + "\uD83D\uDE00", "a".repeat(254) + "\uD83D"}
    };
    for (String[] sample : cases) {
      var model = new CapturingChatModel();
      adapter(model).parse(new ResumeParseModelPort.ParseRequest("1.0", "prompt-1",
          sample[0], List.of(new ResumeParseModelPort.PageText(1, "简历正文"))));
      String user = model.onlyPrompt().getUserMessage().getText();
      var payload = JsonMapper.builder().build().readTree(user.substring(
          "<untrusted-resume>\n".length(), user.indexOf("\n</untrusted-resume>")));
      assertThat(payload.path("fileName").asString()).isEqualTo(sample[1]);
      assertThat(payload.path("pages").get(0).path("text").asString()).isEqualTo("简历正文");
    }
  }

  private static SpringAiFactParseAdapter adapter(ChatModel model) {
    SpringAiFactParseAdapter adapter = new SpringAiFactParseAdapter();
    ReflectionTestUtils.setField(adapter, "clientBuilder", ChatClient.builder(model));
    ReflectionTestUtils.setField(adapter, "json", JsonMapper.builder().build());
    OpenAiChatProperties properties = new OpenAiChatProperties();
    properties.setModel("fallback-model");
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ReflectionTestUtils.setField(
        adapter,
        "resumeSchema",
        new VersionedJsonSchemaOutputConverter("1.0", RESUME_SCHEMA));
    ReflectionTestUtils.setField(
        adapter, "jdSchema", new VersionedJsonSchemaOutputConverter("1.1", JD_SCHEMA));
    ReflectionTestUtils.invokeMethod(adapter, "initializeClient");
    ReflectionTestUtils.setField(adapter, "configuredModel", "fallback-model");
    return adapter;
  }

  private static void assertStructuredSchema(Prompt prompt, String expectedSchema) {
    assertThat(prompt.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
        options -> assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT));
    assertThat(prompt.getSystemMessage().getText()).contains(
        expectedSchema.contains("resume")
            ? JsonMapper.builder().build().readTree(expectedSchema).toString()
            : expectedSchema);
  }

  private static final class CapturingChatModel implements ChatModel {

    private final List<Prompt> prompts = new ArrayList<>();
    private String resumeResponse = "{\"resume\":true}";
    private RuntimeException failure;
    private String finishReason = "stop";

    @Override
    public ChatResponse call(Prompt prompt) {
      prompts.add(prompt);
      if (failure != null) throw failure;
      String content =
          prompt.getUserMessage().getText().contains("<untrusted-resume>")
              ? resumeResponse
              : "{\"jd\":true}";
      ChatResponseMetadata metadata =
          ChatResponseMetadata.builder().model("qwen-test").usage(new DefaultUsage(12, 8)).build();
      var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
      return new ChatResponse(
          List.of(new Generation(new AssistantMessage(content), generationMetadata)), metadata);
    }

    @Override
    public ChatOptions getOptions() {
      return OpenAiChatOptions.builder().build();
    }

    Prompt onlyPrompt() {
      assertThat(prompts).hasSize(1);
      return prompts.getFirst();
    }
  }
}
