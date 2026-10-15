package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;

class IntegrationPropertiesTest {

  private static final String DEFAULT_ARCHIVE_KEY =
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void rejectsDevelopmentArchiveKeyWhenAiParsingIsEnabled() {
    assertThat(archiveKeyViolations(properties(null, true))).hasSize(1);
  }

  @Test
  void rejectsEquivalentUnpaddedDevelopmentArchiveKeyWhenAiParsingIsEnabled() {
    String unpaddedDefault = DEFAULT_ARCHIVE_KEY.substring(0, DEFAULT_ARCHIVE_KEY.length() - 1);

    assertThat(archiveKeyViolations(properties(unpaddedDefault, true))).hasSize(1);
  }

  @Test
  void allowsDevelopmentArchiveKeyWhileAiParsingIsDisabled() {
    assertThat(archiveKeyViolations(properties(null, false))).isEmpty();
  }

  @Test
  void allowsDeploymentSpecificAes256KeyWhenAiParsingIsEnabled() {
    String archiveKey =
        Base64.getEncoder()
            .encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    assertThat(archiveKeyViolations(properties(archiveKey, true))).isEmpty();
  }

  @Test
  void defaultsInfrastructureAdapterModesToOfflineSafeImplementations() {
    IntegrationProperties properties = new IntegrationProperties();

    assertThat(properties.getObjectStorage().getMode()).isEqualTo("local");
    assertThat(properties.getMalwareScan().getMode()).isEqualTo("development");
    assertThat(properties.getMalwareScan().getWriteTimeout()).isEqualTo(Duration.ofMinutes(2));
    assertThat(properties.getSearch().getElasticsearch().getAlias())
        .isEqualTo("resume-facts-current");
    assertThat(properties.getSearch().getElasticsearch().getTimeout())
        .isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.getDocumentExtraction().getMaximumPages()).isEqualTo(100);
    assertThat(properties.getDocumentExtraction().getMaximumExtractedCharacters())
        .isEqualTo(500_000);
    assertThat(properties.getDocumentExtraction().getMaximumContainerExpandedBytes())
        .isEqualTo(67_108_864L);
    assertThat(properties.getDocumentExtraction().getMaximumContainerEntries()).isEqualTo(4_096);
    assertThat(properties.getDocumentExtraction().getMaximumPdfResourceNodes()).isEqualTo(65_536);
    assertThat(properties.getDocumentExtraction().getRenderDpi()).isEqualTo(150);
    assertThat(properties.getDocumentExtraction().getMaximumRenderedPagePixels())
        .isEqualTo(8_000_000L);
    assertThat(properties.getDocumentExtraction().getMaximumRenderedPageDimension())
        .isEqualTo(10_000);
  }

  @Test
  void requiresCompleteHttpsOssConfigurationWhenOssModeIsEnabled() {
    IntegrationProperties properties = new IntegrationProperties();
    properties.getObjectStorage().setMode("oss");

    assertThat(validationMessages(properties))
        .contains("OSS mode requires an HTTPS endpoint, region, bucket and ECS RAM role");

    properties.getObjectStorage().setOss(new IntegrationProperties.Oss(
        "ecs-ram-role",
        "https://oss-cn-hangzhou-internal.aliyuncs.com",
        "cn-hangzhou",
        "vgo-example-private",
        "vgo-example-oss-role"));
    assertThat(validationMessages(properties)).isEmpty();
  }

  @Test
  void rejectsHttpOssEndpointAndStaticCredentialMode() {
    IntegrationProperties properties = new IntegrationProperties();
    properties.getObjectStorage().setMode("oss");
    properties.getObjectStorage().setOss(new IntegrationProperties.Oss(
        "static",
        "http://oss-cn-hangzhou.aliyuncs.com",
        "cn-hangzhou",
        "vgo-example-private",
        "vgo-example-oss-role"));

    assertThat(validator.validate(properties)).isNotEmpty();
  }

  @Test
  void rejectsOssEndpointThatEmbedsCredentialsOrAPath() {
    IntegrationProperties properties = new IntegrationProperties();
    properties.getObjectStorage().setMode("oss");
    properties.getObjectStorage().setOss(new IntegrationProperties.Oss(
        "ecs-ram-role",
        "https://user:secret@oss-cn-hangzhou-internal.aliyuncs.com/private",
        "cn-hangzhou",
        "vgo-example-private",
        "vgo-example-oss-role"));

    assertThat(validationMessages(properties))
        .contains("OSS mode requires an HTTPS endpoint, region, bucket and ECS RAM role");
  }

  @Test
  void rejectsObjectStorageTimeoutsThatDisableDeadlinesOrOverflowTheHttpClient() {
    for (Duration invalid : new Duration[] {null, Duration.ZERO, Duration.ofNanos(1),
        Duration.ofMillis(-1), Duration.ofMillis((long) Integer.MAX_VALUE + 1),
        Duration.ofSeconds(Long.MAX_VALUE)}) {
      for (String phase : new String[] {"connect", "write", "read", "request"}) {
        var properties = new IntegrationProperties();
        var storage = properties.getObjectStorage();
        switch (phase) {
          case "connect" -> storage.setConnectTimeout(invalid);
          case "write" -> storage.setWriteTimeout(invalid);
          case "read" -> storage.setReadTimeout(invalid);
          case "request" -> storage.setRequestTimeout(invalid);
          default -> throw new AssertionError(phase);
        }
        assertThat(validator.validate(properties)).as("%s timeout %s", phase, invalid).isNotEmpty();
      }
    }
    assertThat(validator.validate(new IntegrationProperties())).isEmpty();
  }

  @Test
  void rejectsSocketTimeoutsThatBecomeInfiniteOrOverflowDuringConversion() {
    for (Duration invalid : new Duration[] {null, Duration.ZERO, Duration.ofNanos(1),
        Duration.ofMillis(-1), Duration.ofMillis((long) Integer.MAX_VALUE + 1),
        Duration.ofSeconds(Long.MAX_VALUE)}) {
      for (String phase : new String[] {"connect", "write", "read"}) {
        var properties = new IntegrationProperties();
        var scan = properties.getMalwareScan();
        switch (phase) {
          case "connect" -> scan.setConnectTimeout(invalid);
          case "write" -> scan.setWriteTimeout(invalid);
          case "read" -> scan.setReadTimeout(invalid);
          default -> throw new AssertionError(phase);
        }
        assertThat(validator.validate(properties)).as("%s timeout %s", phase, invalid).isNotEmpty();
      }
    }
  }

  @Test
  void rejectsDocumentPageLimitsOutsideTheSafeConfigurationRange() {
    IntegrationProperties zero = new IntegrationProperties();
    zero.getDocumentExtraction().setMaximumPages(0);
    IntegrationProperties excessive = new IntegrationProperties();
    excessive.getDocumentExtraction().setMaximumPages(1001);

    assertThat(validator.validate(zero))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath().toString())
                    .isEqualTo("documentExtraction.maximumPages"));
    assertThat(validator.validate(excessive))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath().toString())
                    .isEqualTo("documentExtraction.maximumPages"));
  }

  @Test
  void rejectsUnsafeDocumentRasterConfiguration() {
    IntegrationProperties invalid = new IntegrationProperties();
    var extraction = invalid.getDocumentExtraction();
    extraction.setMaximumExtractedCharacters(2_000_001);
    extraction.setMaximumContainerExpandedBytes(1_048_575L);
    extraction.setMaximumContainerEntries(10_001);
    extraction.setMaximumPdfResourceNodes(100_001);
    extraction.setMaximumPdfExpandedBytes(268_435_457L);
    extraction.setMaximumSourceImagePixels(32_000_001L);
    extraction.setMaximumSourceImageDimension(20_001);
    extraction.setRenderDpi(301);
    extraction.setMaximumRenderedPagePixels(16_000_001L);
    extraction.setMaximumRenderedPageDimension(10_001);

    assertThat(validator.validate(invalid))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains(
            "documentExtraction.renderDpi",
            "documentExtraction.maximumExtractedCharacters",
            "documentExtraction.maximumContainerExpandedBytes",
            "documentExtraction.maximumContainerEntries",
            "documentExtraction.maximumPdfResourceNodes",
            "documentExtraction.maximumPdfExpandedBytes",
            "documentExtraction.maximumSourceImagePixels",
            "documentExtraction.maximumSourceImageDimension",
            "documentExtraction.maximumRenderedPagePixels",
            "documentExtraction.maximumRenderedPageDimension");
  }

  @Test
  void rejectsZeroPdfResourceNodeBudget() {
    var properties = new IntegrationProperties();
    properties.getDocumentExtraction().setMaximumPdfResourceNodes(0);
    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("documentExtraction.maximumPdfResourceNodes");
  }

  @Test
  void rejectsIncompleteElasticsearchBasicCredentials() {
    IntegrationProperties properties =
        searchProperties(
            new IntegrationProperties.Elasticsearch(
                "http://localhost:9200",
                "resume-facts-current",
                null,
                "elastic",
                null,
                Duration.ofSeconds(10),
                1));

    assertThat(validationMessages(properties))
        .contains("Elasticsearch username and password must be configured together");
  }

  @Test
  void rejectsElasticsearchPasswordWithoutUsername() {
    IntegrationProperties properties =
        searchProperties(
            new IntegrationProperties.Elasticsearch(
                "http://localhost:9200",
                "resume-facts-current",
                null,
                null,
                "deployment-password",
                Duration.ofSeconds(10),
                1));

    assertThat(validationMessages(properties))
        .contains("Elasticsearch username and password must be configured together");
  }

  @Test
  void rejectsInvalidEndpointWhenElasticsearchModeIsEnabled() {
    IntegrationProperties properties =
        searchProperties(
            new IntegrationProperties.Elasticsearch(
                "file:///tmp/search",
                "resume-facts-current",
                null,
                null,
                null,
                Duration.ofSeconds(10),
                1));

    assertThat(validationMessages(properties))
        .contains("hr.search.elasticsearch.base-url must be an HTTP(S) endpoint without credentials");
  }

  @Test
  void acceptsHttpsEndpointWithApiKeyAuthentication() {
    IntegrationProperties properties =
        searchProperties(
            new IntegrationProperties.Elasticsearch(
                "https://search.internal.example/base/",
                "resume-facts-current",
                "deployment-api-key",
                null,
                null,
                Duration.ofSeconds(10),
                1));

    assertThat(validationMessages(properties)).isEmpty();
  }

  @Test
  void rejectsNegativeElasticsearchReplicaCount() {
    IntegrationProperties properties =
        searchProperties(
            new IntegrationProperties.Elasticsearch(
                "http://localhost:9200",
                "resume-facts-current",
                null,
                null,
                null,
                Duration.ofSeconds(10),
                -1));

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("search.elasticsearch.numberOfReplicas");
  }

  private static Set<ConstraintViolation<IntegrationProperties>> archiveKeyViolations(
      IntegrationProperties properties) {
    return validator.validate(properties).stream()
        .filter(
            violation ->
                "ai.archiveKeyDeploymentSpecificWhenParsingEnabled"
                    .equals(violation.getPropertyPath().toString()))
        .collect(Collectors.toSet());
  }

  private static IntegrationProperties properties(String archiveKey, boolean parsingEnabled) {
    return new IntegrationProperties(
        null,
        null,
        new IntegrationProperties.Ai(
            archiveKey == null ? DEFAULT_ARCHIVE_KEY : archiveKey,
            new IntegrationProperties.Parsing(parsingEnabled)),
        null,
        null);
  }

  private static IntegrationProperties searchProperties(
      IntegrationProperties.Elasticsearch elasticsearch) {
    return new IntegrationProperties(
        null,
        null,
        null,
        new IntegrationProperties.Search("elasticsearch", true, elasticsearch),
        null);
  }

  private static Set<String> validationMessages(IntegrationProperties properties) {
    return validator.validate(properties).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }
}
