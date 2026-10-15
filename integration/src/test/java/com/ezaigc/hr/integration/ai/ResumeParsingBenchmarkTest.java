package com.ezaigc.hr.integration.ai;

import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_PROMPT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.integration.config.MultimodalProperties;
import com.ezaigc.hr.integration.document.LayeredResumeDocumentExtractionAdapter;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.service.DeterministicMatchingEngine;
import com.ezaigc.hr.intelligence.matching.domain.service.ControlledCriteriaEvaluator;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeOcrPort;
import com.ezaigc.hr.talent.application.provider.ResumeMultimodalPagePort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ParseRequest;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator.ContractViolation;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 显式启用的本机真实简历性能基准；报告不得进入版本库。 */
class ResumeParsingBenchmarkTest {

  private static final String SYNTHETIC_JD = "synthetic-java-backend-benchmark-v1; not an actual job evaluation";

  @Test
  void offlineScorerChecksSyntheticConditionsWithoutExternalServices() {
    var resume = new ResumeFactSnapshotData("synthetic-resume", "synthetic-version", "synthetic-candidate", 1,
        new ResumeFactSnapshotData.CandidateFacts(null, null, null, null, null, null, null, null),
        List.of(), List.of(), List.of(), List.of(), List.of(new ResumeFactSnapshotData.SimpleFact(
            "LANGUAGE", "lang", "英语", null, null, null, null)));
    var criterion = new ControlledCriterion();
    criterion.setId("language"); criterion.setCriterionCode("LANGUAGE"); criterion.setLevel("MUST");
    criterion.setOperator("IN"); criterion.setExpectedValueJson("{\"values\":[\"英语\"]}");

    var result = scorer(JsonMapper.builder().build()).evaluate(
        syntheticJd(), resume, plan(), weights(), List.of(criterion), Set.of(), 0);

    assertThat(result.getCriteria()).singleElement().satisfies(item -> {
      assertThat(item.getStatus()).isEqualTo("PASS");
      assertThat(item.getActual()).containsExactly("英语");
      assertThat(item.getExpected()).containsExactly("英语");
    });
    assertThat(result.getMustTier()).isEqualTo("PASS");
    assertThat(result.getBonusScore()).isZero();
  }

  @Test
  void benchmarksEveryLocalPdfWithoutStoppingAfterAnIndividualFailure() throws Exception {
    assumeTrue(Boolean.parseBoolean(System.getenv("HR_RESUME_BENCHMARK")));
    Path input = requiredDirectory("HR_RESUME_BENCHMARK_DIR");
    Path output = requiredOutputDirectory();
    int concurrency = integerEnvironment("HR_RESUME_BENCHMARK_CONCURRENCY", 3, 1, 10);
    int limit = integerEnvironment("HR_RESUME_BENCHMARK_LIMIT", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    String mode = System.getenv().getOrDefault("HR_RESUME_BENCHMARK_MODE", "full")
        .strip().toLowerCase(Locale.ROOT);
    boolean extractOnly = "extract-only".equalsIgnoreCase(mode);
    boolean imageFirst = "image-first".equalsIgnoreCase(mode);
    assertThat(mode).isIn("full", "extract-only", "image-first");
    List<Path> pdfs;
    try (var files = Files.walk(input)) {
      pdfs = files.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .limit(limit).toList();
    }
    assertThat(pdfs).as("benchmark PDF inputs").isNotEmpty();

    long batchStarted = System.nanoTime();
    try (ConfigurableApplicationContext context = benchmarkContext(extractOnly);
        var executor = Executors.newFixedThreadPool(concurrency)) {
      var extraction = context.getBean(LayeredResumeDocumentExtractionAdapter.class);
      var model = extractOnly ? null : context.getBean(SpringAiFactParseAdapter.class);
      var imageTranscriber = imageFirst ? context.getBean("imageTranscriber",
          SpringAiMultimodalPageAdapter.class) : null;
      var validator = context.getBean(ResumeParseContractValidator.class);
      var json = context.getBean(ObjectMapper.class);
      DeterministicMatchingEngine scorer = scorer(json);
      List<Callable<Row>> tasks = new ArrayList<>();
      for (int index = 0; index < pdfs.size(); index++) {
        int sample = index + 1;
        Path pdf = pdfs.get(index);
        tasks.add(() -> {
          Row row = benchmark(sample, pdf, output, extraction, model, imageTranscriber,
              validator, scorer, json);
          Files.writeString(output.resolve(row.id() + ".tsv"), row.tsv(), StandardCharsets.UTF_8);
          System.out.println(row.id() + " success=" + row.success() + " modelMs=" + row.modelMs()
              + " error=" + row.errorCode());
          return row;
        });
      }
      List<Row> rows = new ArrayList<>();
      for (var future : executor.invokeAll(tasks)) rows.add(future.get());
      rows.sort(Comparator.comparingInt(Row::sample));
      long batchElapsedMillis = elapsedMillis(batchStarted);
      writeReports(output, rows, pdfs, mode, concurrency, batchElapsedMillis);
      Files.writeString(output.resolve("batch-elapsed-ms.txt"), Long.toString(batchElapsedMillis));
      long failures = rows.stream().filter(row -> !row.success()).count();
      assertThat(failures).as("failed resumes; inspect the sanitized local report").isZero();
    }
  }

  private static Row benchmark(
      int sample, Path pdf, Path output, LayeredResumeDocumentExtractionAdapter extraction,
      SpringAiFactParseAdapter model, SpringAiMultimodalPageAdapter imageTranscriber,
      ResumeParseContractValidator validator,
      DeterministicMatchingEngine scorer, ObjectMapper json) {
    String id = "R%04d".formatted(sample);
    long extractionMillis = 0, modelMillis = 0, validationMillis = 0, scoreMillis = 0;
    int rawCharacters = 0, formattingNoise = 0, pages = 0, characters = 0, inputTokens = 0, outputTokens = 0;
    int visionInputTokens = 0, visionOutputTokens = 0;
    long visionModelMillis = 0;
    String visionModel = "";
    String hash = "", route = "", error = "", score = "", mustTier = "";
    long started = System.nanoTime();
    String stage = "read";
    try {
      byte[] bytes = Files.readAllBytes(pdf);
      hash = sha256(bytes);
      NativeStats nativeStats = nativeStats(bytes);
      rawCharacters = nativeStats.characters();
      formattingNoise = nativeStats.formattingNoise();
      started = System.nanoTime();
      stage = "extract";
      ExtractedDocument document;
      if (imageTranscriber == null) {
        document = extraction.extract(
            pdf.getFileName().toString(), new ByteArrayInputStream(bytes));
      } else {
        ImageDocument imageDocument = extractEveryPdfPageAsImage(bytes, extraction, imageTranscriber);
        document = imageDocument.document();
        visionInputTokens = imageDocument.inputTokens();
        visionOutputTokens = imageDocument.outputTokens();
        visionModelMillis = imageDocument.modelMillis();
        visionModel = imageDocument.model();
      }
      extractionMillis = elapsedMillis(started);
      pages = document.pageCount();
      characters = document.pages().stream().mapToInt(page -> page.characterCount()).sum();
      route = document.route();
      if (model == null) return new Row(sample, id, hash, rawCharacters, formattingNoise,
          pages, characters, route, extractionMillis, 0, 0, 0, 0, 0,
          visionModel, visionInputTokens, visionOutputTokens, visionModelMillis, "", "", true, "");
      List<PageText> pageTexts = document.pages().stream()
          .map(page -> new PageText(page.pageNo(), page.text())).toList();
      started = System.nanoTime();
      stage = "model";
      var result = model.parse(new ParseRequest(RESUME_FACT_SCHEMA_VERSION,
          RESUME_PARSE_PROMPT_VERSION, pdf.getFileName().toString(), pageTexts));
      modelMillis = elapsedMillis(started);
      Files.writeString(output.resolve(id + "-model.json"), json.writeValueAsString(
          Map.of("provider", result.provider(), "model", result.modelVersion(),
              "promptVersion", RESUME_PARSE_PROMPT_VERSION, "extractorVersion", document.extractorVersion())));
      inputTokens = result.inputTokens();
      outputTokens = result.outputTokens();
      started = System.nanoTime();
      stage = "validation";
      ResumeFacts facts = validator.validateModel(result.rawJson(), pageTexts);
      validationMillis = elapsedMillis(started);
      Files.writeString(output.resolve("facts").resolve(id + ".json"), json.writeValueAsString(facts),
          StandardCharsets.UTF_8);
      started = System.nanoTime();
      stage = "score";
      var evaluation =
          scorer.evaluate(
              syntheticJd(), snapshot(id, facts), plan(), weights(), List.of(), Set.of(), 0);
      scoreMillis = elapsedMillis(started);
      score = evaluation.getReferenceScore().toPlainString();
      mustTier = evaluation.getMustTier();
      return new Row(sample, id, hash, rawCharacters, formattingNoise, pages, characters, route,
          extractionMillis, modelMillis, validationMillis, scoreMillis, inputTokens, outputTokens,
          visionModel, visionInputTokens, visionOutputTokens, visionModelMillis,
          score, mustTier, true, "");
    } catch (IOException | RuntimeException failure) {
      if (Boolean.parseBoolean(System.getenv("HR_RESUME_BENCHMARK_DEBUG"))) {
        failure.printStackTrace(System.err);
      }
      switch (stage) {
        case "extract" -> extractionMillis = elapsedMillis(started);
        case "model" -> modelMillis = elapsedMillis(started);
        case "validation" -> validationMillis = elapsedMillis(started);
        case "score" -> scoreMillis = elapsedMillis(started);
        default -> { }
      }
      error = safeError(failure);
      return new Row(sample, id, hash, rawCharacters, formattingNoise, pages, characters, route,
          extractionMillis, modelMillis, validationMillis, scoreMillis, inputTokens, outputTokens,
          visionModel, visionInputTokens, visionOutputTokens, visionModelMillis,
          score, mustTier, false, error);
    }
  }

  private static ConfigurableApplicationContext benchmarkContext(boolean extractOnly) throws IOException {
    Path root = repositoryRoot();
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(
        new FileSystemResource(root.resolve("bootstrap/src/main/resources/application-dev.yml")));
    var values = yaml.getObject();
    var options = OpenAiChatOptions.builder()
        .apiKey(requiredEnvironment("HR_AI_API_KEY"))
        .baseUrl(values.getProperty("spring.ai.openai.base-url"))
        .model(values.getProperty("spring.ai.openai.chat.model"))
        .timeout(Duration.ofSeconds(120)).maxRetries(0).build();
    var chatModel = OpenAiChatModel.builder().options(options).build();
    var json = JsonMapper.builder().build();
    var model = new SpringAiFactParseAdapter();
    var properties = new OpenAiChatProperties();
    properties.setModel(options.getModel());
    ReflectionTestUtils.setField(model, "clientBuilder", ChatClient.builder(chatModel));
    ReflectionTestUtils.setField(model, "properties", properties);
    ReflectionTestUtils.setField(model, "json", json);
    ReflectionTestUtils.setField(model, "resumeSchema", new VersionedJsonSchemaOutputConverter(
        RESUME_FACT_SCHEMA_VERSION, Files.readString(root.resolve("bootstrap/src/main/resources/schema/resume-parse/1.2.json"))));
    model.initializeClient();
    var vision = new SpringAiMultimodalPageAdapter();
    var visionProperties = new MultimodalProperties();
    visionProperties.setModel(values.getProperty("hr.ai.multimodal.model"));
    visionProperties.setMaxTokens(
        Integer.parseInt(values.getProperty("hr.ai.multimodal.max-tokens", "8192")));
    ReflectionTestUtils.setField(vision, "builder", ChatClient.builder(chatModel));
    ReflectionTestUtils.setField(vision, "properties", visionProperties);
    vision.initializeClient();
    var imageTranscriber = new SpringAiMultimodalPageAdapter();
    var imageTranscriberProperties = new MultimodalProperties();
    imageTranscriberProperties.setModel(options.getModel());
    imageTranscriberProperties.setMaxTokens(
        Integer.parseInt(values.getProperty("hr.ai.multimodal.max-tokens", "8192")));
    ReflectionTestUtils.setField(imageTranscriber, "builder", ChatClient.builder(chatModel));
    ReflectionTestUtils.setField(imageTranscriber, "properties", imageTranscriberProperties);
    imageTranscriber.initializeClient();
    var extraction = new LayeredResumeDocumentExtractionAdapter();
    ResumeOcrPort ocr = (page, image) -> Optional.empty();
    ResumeMultimodalPagePort visionPort = Boolean.parseBoolean(
        values.getProperty("hr.ai.multimodal.enabled", "false"))
        ? vision : (page, image) -> Optional.empty();
    ReflectionTestUtils.setField(extraction, "ocr", ocr);
    ReflectionTestUtils.setField(extraction, "multimodal", visionPort);
    ReflectionTestUtils.setField(extraction, "properties", new IntegrationProperties());
    ReflectionTestUtils.invokeMethod(extraction, "initializeQualityThresholds");
    var validator = new ResumeParseContractValidator();
    ReflectionTestUtils.setField(validator, "objectMapper", json);
    ReflectionTestUtils.setField(validator, "clock", Clock.systemUTC());
    // 只注册已构造的适配器；禁止基准启动业务数据库、Worker、迁移或索引任务。
    var context = new GenericApplicationContext();
    context.getBeanFactory().registerSingleton("extraction", extraction);
    context.getBeanFactory().registerSingleton("model", model);
    context.getBeanFactory().registerSingleton("imageTranscriber", imageTranscriber);
    context.getBeanFactory().registerSingleton("validator", validator);
    context.getBeanFactory().registerSingleton("json", json);
    context.refresh();
    return context;
  }

  /** 强制使用生产 PDFBox 渲染与质量门禁，比较整页图像转录后进入同一事实模型的路径。 */
  @SuppressWarnings("unchecked")
  private static ImageDocument extractEveryPdfPageAsImage(
      byte[] bytes,
      LayeredResumeDocumentExtractionAdapter extraction,
      SpringAiMultimodalPageAdapter transcriber) throws IOException {
    int maximumPages = (int) ReflectionTestUtils.getField(extraction, "maximumPages");
    int maximumCharacters =
        (int) ReflectionTestUtils.getField(extraction, "maximumExtractedCharacters");
    List<ExtractedPage> pages = new ArrayList<>();
    int extractedCharacters = 0;
    int inputTokens = 0;
    int outputTokens = 0;
    long modelMillis = 0;
    String model = "";
    try (var document = Loader.loadPDF(bytes)) {
      if (document.getNumberOfPages() > maximumPages) {
        throw new IOException("DOCUMENT_PAGE_LIMIT");
      }
      PDFRenderer renderer = new PDFRenderer(document);
      for (int index = 0; index < document.getNumberOfPages(); index++) {
        int pageNo = index + 1;
        byte[] image = ReflectionTestUtils.invokeMethod(
            extraction, "render", renderer, document.getPage(index), index, pageNo);
        SpringAiMultimodalPageAdapter.PageTranscription transcription =
            transcriber.transcribe(pageNo, image)
                .orElseThrow(() -> new IOException("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:" + pageNo));
        int remaining = maximumCharacters - extractedCharacters;
        Optional<String> cleaned = ReflectionTestUtils.invokeMethod(
            extraction, "cleanPageCandidate", Optional.of(transcription.text()), remaining, pageNo);
        String text = cleaned.orElseThrow(
            () -> new IOException("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:" + pageNo));
        boolean quality = Boolean.TRUE.equals(
            ReflectionTestUtils.invokeMethod(extraction, "pageQuality", text, !pages.isEmpty()));
        if (!quality) {
          throw new IOException("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:" + pageNo);
        }
        ExtractedPage page = ReflectionTestUtils.invokeMethod(
            extraction, "page", pageNo, "MULTIMODAL", text);
        pages.add(page);
        extractedCharacters += text.length();
        inputTokens += transcription.inputTokens();
        outputTokens += transcription.outputTokens();
        modelMillis += transcription.durationMillis();
        model = model.isEmpty() || model.equals(transcription.model())
            ? transcription.model() : "MIXED";
      }
    }
    if (pages.isEmpty()) {
      throw new IOException("DOCUMENT_EMPTY");
    }
    double printable = pages.stream().mapToDouble(ExtractedPage::printableRatio).average().orElse(0);
    var result = new ExtractedDocument(
        "image-first-pdfbox-production-render-v1", "MULTIMODAL", pages.size(), pages.size(),
        pages.size(), printable, 1, pages);
    return new ImageDocument(result, model, inputTokens, outputTokens, modelMillis);
  }

  private static NativeStats nativeStats(byte[] bytes) throws IOException {
    try (var document = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      String text = stripper.getText(document);
      int noise = (int) text.codePoints().filter(ResumeParsingBenchmarkTest::isFormattingNoise).count();
      return new NativeStats(text.length(), noise);
    }
  }

  private static boolean isFormattingNoise(int value) {
    int type = Character.getType(value);
    return value == 0 || value == 0xFFFD
        || Character.isISOControl(value) && value != '\r' && value != '\n' && value != '\t'
        || type == Character.FORMAT || type == Character.PRIVATE_USE || type == Character.UNASSIGNED;
  }

  private static void writeReports(Path output, List<Row> rows, List<Path> files,
      String mode, int concurrency, long batchElapsedMillis) throws IOException {
    String header = "sample\tsha256\trawChars\tformatNoise\tcleanChars\tpages\troute"
        + "\textractMs\tmodelMs\tvalidationMs\tscoreMs\tinputTokens\toutputTokens"
        + "\tvisionModel\tvisionInputTokens\tvisionOutputTokens\tvisionModelMs"
        + "\tsyntheticScore\tmustTier\tsuccess\terrorCode\n";
    StringBuilder report = new StringBuilder(header);
    rows.forEach(row -> report.append(row.tsv()).append('\n'));
    Files.writeString(output.resolve("resume-benchmark.tsv"), report, StandardCharsets.UTF_8);
    StringBuilder mapping = new StringBuilder("sample\tsha256\tfileName\n");
    for (int index = 0; index < rows.size(); index++) {
      mapping.append(rows.get(index).id()).append('\t').append(rows.get(index).sha256()).append('\t')
          .append(files.get(index).getFileName().toString().replace('\t', ' ')).append('\n');
    }
    Files.writeString(output.resolve("resume-benchmark-filename-mapping.tsv"), mapping, StandardCharsets.UTF_8);
    long succeeded = rows.stream().filter(Row::success).count();
    long totalMillis = rows.stream().mapToLong(row -> row.extractionMs() + row.modelMs()
        + row.validationMs() + row.scoreMs()).sum();
    List<Long> modelDurations = rows.stream().filter(Row::success).map(Row::modelMs).sorted().toList();
    List<Long> outputTokens = rows.stream().filter(Row::success)
        .map(row -> (long) row.outputTokens()).sorted().toList();
    double throughputPerMinute = batchElapsedMillis == 0 ? 0.0
        : rows.size() * 60_000.0 / batchElapsedMillis;
    String summary = "benchmark=" + SYNTHETIC_JD + "\nmode=" + mode
        + "\nconcurrency=" + concurrency + "\nsamples=" + rows.size() + "\nsucceeded=" + succeeded
        + "\nfailed=" + (rows.size() - succeeded) + "\nbatchElapsedMs=" + batchElapsedMillis
        + "\nthroughputPerMinute=" + String.format(Locale.ROOT, "%.2f", throughputPerMinute)
        + "\ntotalRecordedMs=" + totalMillis
        + "\nmodelP50Ms=" + percentile(modelDurations, 0.50)
        + "\nmodelP95Ms=" + percentile(modelDurations, 0.95)
        + "\nmodelP99Ms=" + percentile(modelDurations, 0.99)
        + "\nmodelMaxMs=" + maximum(modelDurations)
        + "\noutputTokensP95=" + percentile(outputTokens, 0.95)
        + "\noutputTokensP99=" + percentile(outputTokens, 0.99)
        + "\noutputTokensMax=" + maximum(outputTokens) + "\n";
    Files.writeString(output.resolve("resume-benchmark-summary.txt"), summary, StandardCharsets.UTF_8);
  }

  private static long percentile(List<Long> sortedValues, double percentile) {
    if (sortedValues.isEmpty()) return 0;
    int index = Math.max(0, (int) Math.ceil(sortedValues.size() * percentile) - 1);
    return sortedValues.get(index);
  }

  private static long maximum(List<Long> sortedValues) {
    return sortedValues.isEmpty() ? 0 : sortedValues.getLast();
  }

  private static Path requiredDirectory(String name) {
    String value = requiredEnvironment(name);
    Path path = Path.of(value).toAbsolutePath().normalize();
    if (!Files.isDirectory(path)) throw new IllegalStateException(name + "_INVALID");
    return path;
  }

  private static Path requiredOutputDirectory() throws IOException {
    String value = requiredEnvironment("HR_RESUME_BENCHMARK_OUTPUT");
    Path path = Path.of(value).toAbsolutePath().normalize();
    Files.createDirectories(path.resolve("facts"));
    return path;
  }

  private static int integerEnvironment(String name, int fallback, int minimum, int maximum) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) return fallback;
    int parsed = Integer.parseInt(value);
    if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException(name + "_INVALID");
    return parsed;
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + "_REQUIRED");
    return value;
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("bootstrap"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("REPOSITORY_ROOT_MISSING");
  }

  private static long elapsedMillis(long start) {
    return Math.max(0, (System.nanoTime() - start) / 1_000_000);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA256_UNAVAILABLE", impossible);
    }
  }

  private static String safeError(Throwable failure) {
    Throwable value = failure;
    while (value != null) {
      if (value instanceof ContractViolation violation) return violation.errorCode();
      if (value instanceof IOException && value.getMessage() != null
          && value.getMessage().matches("[A-Z_]+(?::[0-9]+)?")) return value.getMessage();
      value = value.getCause();
    }
    return failure instanceof IOException ? "DOCUMENT_EXTRACTION_FAILED" : "MODEL_OR_VALIDATION_FAILED";
  }

  private static DeterministicMatchingEngine scorer(ObjectMapper json) {
    var engine = new DeterministicMatchingEngine();
    var controlled = new ControlledCriteriaEvaluator();
    ReflectionTestUtils.setField(controlled, "json", json);
    ReflectionTestUtils.setField(engine, "controlledCriteria", controlled);
    ReflectionTestUtils.setField(engine, "clock", Clock.systemUTC());
    return engine;
  }

  private static ResumeFactSnapshotData snapshot(String id, ResumeFacts facts) {
    var c = facts.candidate();
    return new ResumeFactSnapshotData(id, id, id, 1,
        new ResumeFactSnapshotData.CandidateFacts(
            null, null, null, c.currentRole(), c.city(), null, null, c.jobIntention()),
        facts.workExperiences().stream().map(v -> new ResumeFactSnapshotData.Work(v.id(), v.employer(), v.role(),
            null, null, v.description(), v.responsibilities(), v.achievements(), v.startDate(), v.endDate())).toList(),
        facts.projectExperiences().stream().map(v -> new ResumeFactSnapshotData.Project(v.id(), v.projectName(),
            v.role(), null, null, v.description(), v.technologies(), v.achievements())).toList(),
        facts.educations().stream().map(v -> new ResumeFactSnapshotData.Education(v.id(), v.school(), v.degree(),
            v.major(), null, null, v.learningMode())).toList(),
        facts.skills().stream().map(v -> new ResumeFactSnapshotData.Skill(v.id(), v.name(), v.level(),
            v.years() == null ? null : BigDecimal.valueOf(v.years()))).toList(), List.of());
  }

  private static JdFactSnapshotData syntheticJd() {
    return new JdFactSnapshotData("synthetic-jd", "synthetic-jdv", 1,
        new JdFactSnapshotData.ProfessionalFacts(
            List.of(new JdFactSnapshotData.Skill("java", "Java", true, List.of()),
                new JdFactSnapshotData.Skill("spring", "Spring Boot", false, List.of("Spring"))),
            List.of(new JdFactSnapshotData.Role("backend", "后端工程师", null)), BigDecimal.valueOf(3),
            BigDecimal.valueOf(5), List.of("系统开发", "问题排查"), List.of("互联网"), List.of("平台")),
        new JdFactSnapshotData.ControlledFacts(null, null, null, List.of(), List.of(), List.of()),
        new JdFactSnapshotData.FulfillmentFacts(List.of(), null, null, null, null));
  }

  private static MatchPlanVersion plan() {
    MatchPlanVersion plan = new MatchPlanVersion();
    plan.setId("synthetic-plan");
    plan.setPlanId("synthetic");
    plan.setJdVersionId("synthetic-jdv");
    plan.setSkillsEnabled(true);
    plan.setRoleEnabled(true);
    plan.setYearsEnabled(true);
    plan.setResponsibilityEnabled(true);
    plan.setIndustryProjectEnabled(true);
    return plan;
  }

  private static WeightProfileVersion weights() {
    return new WeightProfileVersion("synthetic-weights", "synthetic", 1, 20, 20, 20, 20, 20);
  }

  private record NativeStats(int characters, int formattingNoise) {}

  private record ImageDocument(
      ExtractedDocument document, String model, int inputTokens, int outputTokens, long modelMillis) {}

  private record Row(int sample, String id, String sha256, int rawCharacters, int formattingNoise,
      int pages, int cleanCharacters, String route, long extractionMs, long modelMs,
      long validationMs, long scoreMs, int inputTokens, int outputTokens,
      String visionModel, int visionInputTokens, int visionOutputTokens, long visionModelMs,
      String syntheticScore, String mustTier, boolean success, String errorCode) {
    String tsv() {
      return String.join("\t", id, sha256, String.valueOf(rawCharacters), String.valueOf(formattingNoise),
          String.valueOf(cleanCharacters), String.valueOf(pages), route, String.valueOf(extractionMs),
          String.valueOf(modelMs), String.valueOf(validationMs), String.valueOf(scoreMs),
          String.valueOf(inputTokens), String.valueOf(outputTokens),
          visionModel, String.valueOf(visionInputTokens), String.valueOf(visionOutputTokens),
          String.valueOf(visionModelMs), syntheticScore, mustTier,
          String.valueOf(success), errorCode);
    }
  }
}
