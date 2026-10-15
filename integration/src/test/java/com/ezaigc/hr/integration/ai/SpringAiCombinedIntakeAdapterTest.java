package com.ezaigc.hr.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.talent.application.provider.CombinedIntakeModelPort.Request;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.constant.TalentConstants;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class SpringAiCombinedIntakeAdapterTest {

  private static final String RESUME_SCHEMA = """
      {"type":"object","additionalProperties":false,
       "required":["candidate","workExperiences","unknownItems"],
       "properties":{"candidate":{"type":"object","required":["fullName"],
         "properties":{"fullName":{"type":["string","null"]},
           "email":{"type":["string","null"]},"phone":{"type":["string","null"]},
           "age":{"type":["integer","null"]},"birthDate":{"type":["string","null"]}}},
         "workExperiences":{"type":"array","items":{"$ref":"#/$defs/work"}},
         "unknownItems":{"type":"array","items":{"type":"string"}}},
       "$defs":{"work":{"type":"object","properties":{"id":{"type":"string"}}}}}
      """;

  @Test
  void usesOnePromptWithResolvableCompactSchemaAndNoEvidencePayload() {
    var model = new CapturingChatModel();
    var adapter = adapter(model);

    var result = adapter.evaluate(request());

    assertThat(result.rawJson()).isEqualTo(model.response);
    assertThat(result.inputTokens()).isEqualTo(20);
    assertThat(result.outputTokens()).isEqualTo(12);
    Prompt prompt = model.onlyPrompt();
    assertThat(prompt.getUserMessage().getText()).contains(
        "<untrusted-jd-facts>", "<untrusted-resume>", "简历正文", "2026-09-21");
    assertThat(prompt.getSystemMessage().getText()).contains(
        "一次读取简历正文和已冻结 JD 事实", "信息不足", "不输出待核实项、证据、路径、引用、置信度或可靠性",
        "逐页核对候选人本人的手机号和邮箱", "明确且格式有效时不得返回 null",
        "candidate.age 只提取正文明确写出的候选人年龄", "年龄：25", "不得提取岗位年龄要求或他人年龄",
        "candidate.birthDate 只提取明确的出生日期",
        "只有出生信息时 age 返回 null，由后端计算周岁", "不得根据毕业时间、工作年限、身份证号码或文件名推测或补全");
    assertThat(prompt.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
      assertThat(options.getMaxTokens()).isEqualTo(6500);
      assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
      var schema = JsonMapper.builder().build()
          .readTree(options.getResponseFormat().getJsonSchema());
      assertThat(schema.at("/properties/resumeFacts/properties/unknownItems").isMissingNode())
          .isTrue();
      assertThat(schema.at("/properties/resumeFacts/required").toString())
          .doesNotContain("unknownItems");
      assertThat(schema.at("/properties/resumeFacts/properties/candidate/required").toString())
          .contains("fullName", "email", "phone");
      assertThat(schema.at("/properties/resumeFacts/properties/candidate/properties/age/type").toString())
          .contains("integer", "null");
      assertThat(schema.at("/properties/resumeFacts/properties/candidate/properties/birthDate/type").toString())
          .contains("string", "null");
      assertThat(schema.at("/$defs/work/properties/id/type").stringValue()).isEqualTo("string");
      assertThat(schema.at("/properties/resumeFacts/properties/workExperiences/items/$ref")
          .stringValue()).isEqualTo("#/$defs/work");
      assertThat(schema.at("/properties/assessment/properties").propertyNames())
          .containsExactlyInAnyOrder("dimensions", "professionalScore", "summary");
      assertThat(options.getResponseFormat().getJsonSchema())
          .doesNotContain("evidence", "confidence", "reliability");
    });
  }

  @Test
  void rejectsStaleContractsBeforeCallingModel() {
    var model = new CapturingChatModel();
    var adapter = adapter(model);
    Request stale = new Request(TalentConstants.RESUME_FACT_SCHEMA_VERSION,
        TalentConstants.RESUME_PARSE_PROMPT_VERSION, "old-schema",
        AiScoringConstants.PROMPT_VERSION, LocalDate.of(2026, 9, 21),
        "resume.pdf", List.of(new PageText(1, "简历正文")), "{\"professional\":{}}");

    assertThatThrownBy(() -> adapter.evaluate(stale))
        .isInstanceOf(IllegalArgumentException.class);
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
      Request base = request();
      adapter(model).evaluate(new Request(base.resumeSchemaVersion(), base.resumePromptVersion(),
          base.scoringSchemaVersion(), base.scoringPromptVersion(), base.evaluatedOn(),
          sample[0], base.pages(), base.jdFactsJson()));
      String user = model.onlyPrompt().getUserMessage().getText();
      int start = user.indexOf("<untrusted-resume>\n") + "<untrusted-resume>\n".length();
      var payload = JsonMapper.builder().build()
          .readTree(user.substring(start, user.indexOf("\n</untrusted-resume>")));
      assertThat(payload.path("fileName").asString()).isEqualTo(sample[1]);
      assertThat(payload.path("pages").get(0).path("text").asString()).isEqualTo("简历正文");
    }
  }

  private static Request request() {
    return new Request(TalentConstants.RESUME_FACT_SCHEMA_VERSION,
        TalentConstants.RESUME_PARSE_PROMPT_VERSION, AiScoringConstants.SCHEMA_VERSION,
        AiScoringConstants.PROMPT_VERSION, LocalDate.of(2026, 9, 21),
        "resume.pdf", List.of(new PageText(1, "简历正文")), "{\"professional\":{}}");
  }

  private static SpringAiCombinedIntakeAdapter adapter(ChatModel model) {
    var adapter = new SpringAiCombinedIntakeAdapter();
    ReflectionTestUtils.setField(adapter, "clientBuilder", ChatClient.builder(model));
    ReflectionTestUtils.setField(adapter, "json", JsonMapper.builder().build());
    ReflectionTestUtils.setField(adapter, "resumeSchema",
        new VersionedJsonSchemaOutputConverter(
            TalentConstants.RESUME_FACT_SCHEMA_VERSION, RESUME_SCHEMA));
    var properties = new OpenAiChatProperties();
    properties.setModel("qwen3.8-flash");
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ReflectionTestUtils.invokeMethod(adapter, "initializeClient");
    return adapter;
  }

  private static final class CapturingChatModel implements ChatModel {
    private final List<Prompt> prompts = new ArrayList<>();
    private final String response = "{\"resumeFacts\":{},\"assessment\":{}}";

    @Override
    public ChatResponse call(Prompt prompt) {
      prompts.add(prompt);
      var metadata = ChatResponseMetadata.builder().model("qwen-response-model")
          .usage(new DefaultUsage(20, 12)).build();
      var generation = ChatGenerationMetadata.builder().finishReason("stop").build();
      return new ChatResponse(
          List.of(new Generation(new AssistantMessage(response), generation)), metadata);
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
