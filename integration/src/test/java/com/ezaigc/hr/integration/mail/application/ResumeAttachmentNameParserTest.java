package com.ezaigc.hr.integration.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeAttachmentNameParserTest {

  @Test
  void parsesCurrentAndCodedFormatsWithoutFuzzyRegionChanges() {
    var current = ResumeAttachmentNameParser.parse("【Java 开发_东莞_20k】张三_5年.pdf");
    assertThat(current.jdCode()).isNull();
    assertThat(current.title()).isEqualTo("Java 开发");
    assertThat(current.region()).isEqualTo("东莞");

    var coded = ResumeAttachmentNameParser.parse(
        "【JD-01JABCDEF23456789GHJKMNPQR_Java_高级开发_东莞市_20k】李四.docx");
    assertThat(coded.jdCode()).isEqualTo("JD-01JABCDEF23456789GHJKMNPQR");
    assertThat(coded.title()).isEqualTo("Java_高级开发");
    assertThat(coded.region()).isEqualTo("东莞市");
    assertThat(coded.region()).isNotEqualTo(current.region());

    var unicodeCode = ResumeAttachmentNameParser.parse(
        "【ＪＤ－０１ＪＡＢＣＤＥＦ２３４５６７８９ＧＨＪＫＭＮＰＱＲ_Java 开发_东莞_20k】王五.pdf");
    assertThat(unicodeCode.jdCode()).isEqualTo("JD-01JABCDEF23456789GHJKMNPQR");
  }

  @Test
  void parsesThreeDigitGroupsAndGrowingSequenceFromBothSources() {
    for (String code : new String[] {"JD-901902001", "JD-9019021000"}) {
      var parsed = ResumeAttachmentNameParser.parse("【" + code + "_运维工程师_杭州_20k】张三.pdf",
          "【" + code + "】应聘");
      assertThat(parsed.jdCodes()).containsExactly(code);
      assertThat(parsed.jdCode()).isEqualTo(code);
      assertThat(parsed.title()).isEqualTo("运维工程师");
      assertThat(parsed.region()).isEqualTo("杭州");
    }
  }

  @Test
  void extractsCodesAnywhereAndDeduplicatesAcrossSubjectAndAttachment() {
    var parsed = ResumeAttachmentNameParser.parse("张三_JD-000001.pdf", "回复：【ＪＤ－０００００１】Java开发");
    assertThat(parsed.jdCodes()).containsExactly("JD-000001");
    assertThat(parsed.title()).isNull();
    assertThat(parsed.region()).isNull();
    assertThat(ResumeAttachmentNameParser.parse("张三.pdf", "【JD-1000000】申请").jdCode())
        .isEqualTo("JD-1000000");
    assertThat(ResumeAttachmentNameParser.parse("XJD-000001.pdf").jdCodes())
        .containsExactly("JD-000001");
    assertThat(ResumeAttachmentNameParser.parse("JD-000001X.pdf").jdCode()).isNull();
    assertThat(ResumeAttachmentNameParser.parse("JD-" + "1".repeat(100) + ".pdf").jdCode()).isNull();
  }

  @Test
  void extractsCompleteCodesAfterAnyBusinessPrefixWithoutAssumingFixedLength() {
    for (String code : new String[] {
        "JD-000001", "JD-90190200304", "JD-901902003005", "JD-01JABCDEF23456789GHJKMNPQR"}) {
      for (String prefix : new String[] {"EXAMPLE", "BJ", "2026", "客户A"}) {
        String fileName = "【运维工程师~" + prefix + code + "_上海_7-8K】示例_4年.pdf";
        String subject = "应聘运维工程师~" + prefix + code + " | 上海7-8K【BOSS直聘】";
        for (var parsed : List.of(
            ResumeAttachmentNameParser.parse(fileName),
            ResumeAttachmentNameParser.parse("示例.pdf", subject),
            ResumeAttachmentNameParser.parse(fileName, "申请" + code))) {
          assertThat(parsed.jdCodes()).containsExactly(code);
          assertThat(parsed.jdCode()).isEqualTo(code);
        }
        assertThat(ResumeAttachmentNameParser.parse(fileName).region()).isEqualTo("上海");
      }
    }
    assertThat(ResumeAttachmentNameParser.parse("示例.pdf", "ＥＸＡＭＰＬＥＪＤ－９０１９０２００３０４").jdCode())
        .isEqualTo("JD-90190200304");
  }

  @Test
  void preservesUnknownAndMalformedPrefixedTokensInsteadOfTruncatingThem() {
    for (String token : new String[] {"JD-901902003040", "JD-90190200304X", "JD-123", "JD-"}) {
      var parsed = ResumeAttachmentNameParser.parse("EXAMPLE" + token + ".pdf", "申请EXAMPLE" + token);
      assertThat(parsed.jdCodes()).containsExactly(token);
    }
    assertThat(ResumeAttachmentNameParser.parse("EXAMPLEJD-90190200304X.pdf").jdCode()).isNull();
  }
}
