package com.ezaigc.hr.integration.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService.Target;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailResumeMatcherTest {

  private static final Target TARGET = new Target("jd", "version", "JD-01JABCDEF23456789GHJKMNPQR",
      "Java 开发", 3, "owner", "department", List.of("东莞"), true);

  @Test
  void routesOnlyExactCodeTitleAndRegion() {
    var byCode = MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【JD-01JABCDEF23456789GHJKMNPQR_Java 开发_东莞_20k】张三.pdf"),
        List.of(TARGET), null);
    assertThat(byCode.matchType()).isEqualTo("JD_CODE");

    var wrongRegion = MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【JD-01JABCDEF23456789GHJKMNPQR_Java 开发_东莞市_20k】张三.pdf"),
        List.of(TARGET), null);
    assertThat(wrongRegion.failureCode()).isEqualTo("JD_CODE_REGION_CONFLICT");

    var exact = MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【Java 开发_东莞_20k】张三.pdf"), List.of(TARGET), null);
    assertThat(exact.matchType()).isEqualTo("EXACT_TITLE_REGION");
  }

  @Test
  void rejectsAmbiguousAndInvalidOwnerTargets() {
    var parsed = ResumeAttachmentNameParser.parse("【Java 开发_东莞_20k】张三.pdf");
    Target second = new Target("jd2", "version2", "JD-01JABCDEF23456789GHJKMNPRR",
        "Java 开发", 1, "owner2", "department", List.of("东莞"), true);
    assertThat(MailResumeMatcher.match(parsed, List.of(TARGET, second), null).failureCode())
        .isEqualTo("JD_EXACT_MATCH_AMBIGUOUS");
    Target invalid = new Target("jd", "version", null, "Java 开发", 1, null, null, List.of("东莞"), false);
    assertThat(MailResumeMatcher.match(parsed, List.of(invalid), null).failureCode())
        .isEqualTo("JD_OWNER_INVALID");
  }

  @Test
  void sendsMissingUnavailableAndClosedTargetsToReview() {
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("简历.pdf"), List.of(TARGET), null).failureCode())
        .isEqualTo("JD_FIELDS_MISSING");
    var parsed = ResumeAttachmentNameParser.parse("【Java 开发_深圳_20k】张三.pdf");
    assertThat(MailResumeMatcher.match(parsed, List.of(TARGET), null).failureCode())
        .isEqualTo("JD_EXACT_MATCH_NOT_FOUND");
    assertThat(MailResumeMatcher.match(parsed, List.of(TARGET), "closed-jd").failureCode())
        .isEqualTo("CONFIRMED_RULE_JD_UNAVAILABLE");
  }

  @Test
  void routesShortCodesFromEitherSourceWithoutRequiringRegionOrCurrentTitle() {
    Target shortCode = new Target("short", "v2", "JD-000001", "IDC工程师", 2,
        "owner", "department", List.of("杭州"), true);
    for (var parsed : List.of(
        ResumeAttachmentNameParser.parse("张三.pdf", "【JD-000001】旧岗位名称"),
        ResumeAttachmentNameParser.parse("张三-JD-000001.pdf"),
        ResumeAttachmentNameParser.parse("【JD-000001】张三.pdf", "【JD-000001】申请"))) {
      var route = MailResumeMatcher.match(parsed, List.of(shortCode), null);
      assertThat(route.matchType()).isEqualTo("JD_CODE");
      assertThat(route.target()).isEqualTo(shortCode);
    }
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【旧岗位_上海_20k】张三.pdf", "【JD-000001】申请"),
        List.of(shortCode), null).failureCode()).isEqualTo("JD_CODE_REGION_CONFLICT");
  }

  @Test
  void routesIndependentHrJdsByGrowingCodeAndReviewsAmbiguityDespiteSavedRule() {
    Target first = new Target("jd-a", "version-a", "JD-01001002001", "运维工程师", 3,
        "hr-a", "department-a", List.of("杭州"), true);
    Target second = new Target("jd-b", "version-b", "JD-010010021000", "运维工程师", 2,
        "hr-b", "department-a", List.of("杭州"), true);
    Target third = new Target("jd-c", "version-c", "JD-02001002001", "运维工程师", 1,
        "hr-c", "department-b", List.of("杭州"), true);
    var targets = List.of(first, second, third);
    for (Target target : targets) {
      var parsed = ResumeAttachmentNameParser.parse("【" + target.code() + "_运维工程师_杭州_20k】简历.pdf",
          "应聘运维工程师【" + target.code() + "】");
      var route = MailResumeMatcher.match(parsed, targets, first.jdId());
      assertThat(route.matchType()).isEqualTo("JD_CODE");
      assertThat(route.target().ownerUserId()).isEqualTo(target.ownerUserId());
      assertThat(route.target().jdVersionId()).isEqualTo(target.jdVersionId());
    }
    var ambiguous = MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【运维工程师_杭州_20k】简历.pdf"), targets, first.jdId());
    assertThat(ambiguous.matchType()).isEqualTo("REVIEW_REQUIRED");
    assertThat(ambiguous.target()).isNull();
    assertThat(ambiguous.failureCode()).isEqualTo("JD_EXACT_MATCH_AMBIGUOUS");
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【" + first.code() + "】简历.pdf", second.code()),
        targets, first.jdId()).failureCode()).isEqualTo("JD_CODE_CONFLICT");
  }

  @Test
  void failsClosedForUnknownMalformedConflictingAndOwnerlessCodes() {
    Target shortCode = new Target("short", "v2", "JD-000001", "Java 开发", 2,
        "owner", "department", List.of("东莞"), true);
    for (String code : List.of("JD-999999", "JD-123", "JD-000001X")) {
      var parsed = ResumeAttachmentNameParser.parse("【Java 开发_东莞_20k】张三.pdf", code);
      assertThat(MailResumeMatcher.match(parsed, List.of(shortCode), "short").failureCode())
          .isEqualTo("JD_CODE_NOT_FOUND");
    }
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【JD-000001】张三.pdf", TARGET.code()),
        List.of(shortCode, TARGET), null).failureCode()).isEqualTo("JD_CODE_CONFLICT");
    Target invalid = new Target("short", "v2", "JD-000001", "Java 开发", 2,
        null, null, List.of("东莞"), false);
    assertThat(MailResumeMatcher.match(ResumeAttachmentNameParser.parse("【JD-000001】张三.pdf"),
        List.of(invalid), null).failureCode()).isEqualTo("JD_OWNER_INVALID");
    Target alias = new Target("short", "v2", TARGET.code(), "Java 开发", 2,
        "owner", "department", List.of("东莞"), true);
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【JD-000001】张三.pdf", TARGET.code()),
        List.of(shortCode, alias), null).matchType()).isEqualTo("JD_CODE");
  }

  @Test
  void routesBossMailByCompleteCodeDespiteDifferentTitleAndSameRegionJds() {
    Target first = new Target("jd-a", "v-a", "JD-90190200303", "IDC运维工程师", 1,
        "hr-a", "department", List.of("上海"), true);
    Target second = new Target("jd-b", "v-b", "JD-90190200304", "IDC运维工程师", 2,
        "hr-b", "department", List.of("上海"), true);
    Target growing = new Target("jd-c", "v-c", "JD-901902003005", "IDC运维工程师", 1,
        "hr-c", "department", List.of("上海"), true);
    var targets = List.of(first, second, growing);
    for (Target target : targets) {
      var parsed = ResumeAttachmentNameParser.parse(
          "【运维工程师~EXAMPLE" + target.code() + "_上海_7-8K】示例_4年.pdf",
          "示例 | 4年，应聘 运维工程师~EXAMPLE" + target.code() + " | 上海7-8K【BOSS直聘】");
      var route = MailResumeMatcher.match(parsed, targets, first.jdId());
      assertThat(parsed.jdCodes()).containsExactly(target.code());
      assertThat(route.matchType()).isEqualTo("JD_CODE");
      assertThat(route.target()).isEqualTo(target);
    }
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("示例.pdf", "应聘EXAMPLEJD-90190200304"), targets, null).target())
        .isEqualTo(second);
  }

  @Test
  void prefixedCodesCannotBypassUnknownConflictRegionOrOwnerChecks() {
    Target first = new Target("jd-a", "v-a", "JD-90190200303", "IDC运维工程师", 1,
        "hr-a", "department", List.of("上海"), true);
    Target second = new Target("jd-b", "v-b", "JD-90190200304", "IDC运维工程师", 1,
        "hr-b", "department", List.of("上海"), true);
    var targets = List.of(first, second);
    for (String token : List.of("JD-901902003040", "JD-90190200304X", "JD-999999", "JD-123", "JD-")) {
      for (String subject : List.of("申请EXAMPLE" + token, "EXAMPLEJD-90190200304 / EXAMPLE" + token)) {
        var parsed = ResumeAttachmentNameParser.parse("【IDC运维工程师_上海_7-8K】示例.pdf", subject);
        assertThat(MailResumeMatcher.match(parsed, List.of(second), second.jdId()).failureCode())
            .isEqualTo("JD_CODE_NOT_FOUND");
      }
    }
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【运维工程师~EXAMPLEJD-90190200304_上海_7-8K】示例.pdf",
            "申请EXAMPLEJD-90190200303"), targets, second.jdId()).failureCode())
        .isEqualTo("JD_CODE_CONFLICT");
    assertThat(MailResumeMatcher.match(
        ResumeAttachmentNameParser.parse("【运维工程师~EXAMPLEJD-90190200304_北京_7-8K】示例.pdf"),
        targets, second.jdId()).failureCode()).isEqualTo("JD_CODE_REGION_CONFLICT");
    var parsed = ResumeAttachmentNameParser.parse("示例.pdf", "申请EXAMPLEJD-90190200304");
    assertThat(MailResumeMatcher.match(parsed, List.of(first), first.jdId()).failureCode())
        .isEqualTo("JD_CODE_NOT_FOUND");
    Target invalid = new Target("jd-b", "v-b", second.code(), second.title(), 1,
        null, null, second.regions(), false);
    assertThat(MailResumeMatcher.match(parsed, List.of(invalid), null).failureCode())
        .isEqualTo("JD_OWNER_INVALID");
  }
}
