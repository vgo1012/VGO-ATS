package com.ezaigc.hr.integration.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeMultimodalPagePort;
import com.ezaigc.hr.talent.application.provider.ResumeOcrPort;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

class LayeredResumeDocumentExtractionAdapterTest {

  @Test
  void removesTransparentDiagonalWatermarkButPreservesVisibleAndOcrText() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawLine(content, 40, 750, "Resume experience Java C++ C# .NET");
        drawPaintedText(content, "watermarkTracking123", 0.00784f, 40, RenderingMode.FILL, 400);
        drawPaintedText(content, "VisibleRotation", 1f, 40, RenderingMode.FILL, 300);
        drawPaintedText(content, "HiddenOcrExperience", 0f, 0, RenderingMode.NEITHER, 200);
        drawPaintedText(content, "HorizontalText", 0.00784f, 0, RenderingMode.FILL, 100);
      }
      document.save(output);
      bytes = output.toByteArray();
    }
    var document = adapter((page, image) -> Optional.empty(), (page, image) -> Optional.empty())
        .extract("synthetic-watermark.pdf", new ByteArrayInputStream(bytes));
    String text = document.pages().getFirst().text();
    assertThat(text).doesNotContain("watermarkTracking123")
        .contains("HiddenOcrExperience", "HorizontalText", "Java C++ C# .NET");
    assertThat(text.replaceAll("\\s", "")).contains("VisibleRotation").doesNotContain("watermarkTracking123");
    assertThat(document.route()).isEqualTo("NATIVE_TEXT");
  }

  private static void drawPaintedText(PDPageContentStream content, String text, float opacity,
      double degrees, RenderingMode renderingMode, float y) throws IOException {
    content.saveGraphicsState();
    var state = new PDExtendedGraphicsState();
    state.setNonStrokingAlphaConstant(opacity);
    content.setGraphicsStateParameters(state);
    content.beginText();
    content.setRenderingMode(renderingMode);
    content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(degrees), 40, y));
    content.showText(text);
    content.endText();
    content.restoreGraphicsState();
  }

  @Test
  void ordersRightAlignedDatesWithTheirVisualEmploymentRowInsteadOfPdfDrawingOrder() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawLine(content, 390, 700, "2026-06 to PRESENT");
        drawLine(content, 390, 650, "2025-08 to 2025-12");
        drawLine(content, 40, 750, "Resume - Work experience and skills");
        drawLine(content, 40, 700, "Example Alpha Ltd - Systems Engineer");
        drawLine(content, 40, 680, "Responsibilities: Linux server maintenance and troubleshooting.");
        drawLine(content, 40, 650, "Example Beta Ltd - Network Engineer");
        drawLine(content, 40, 630, "Responsibilities: TCP/IP network maintenance and equipment support.");
      }
      document.save(output);
      bytes = output.toByteArray();
    }
    var value = adapter((page, image) -> {throw new AssertionError("native text should be usable");},
        (page, image) -> {throw new AssertionError("native text should not need vision");});
    var result = value.extract("synthetic-columns.pdf", new ByteArrayInputStream(bytes));
    assertThat(result.pages().getFirst().text())
        .containsPattern("Example Alpha Ltd - Systems Engineer[^\\n]*2026-06 to PRESENT")
        .containsPattern("Example Beta Ltd - Network Engineer[^\\n]*2025-08 to 2025-12");
  }

  private static void drawLine(PDPageContentStream content, float x, float y, String text) throws Exception {
    content.beginText();
    content.newLineAtOffset(x, y);
    content.showText(text);
    content.endText();
  }

  @Test
  void acceptsResumeCoverWithJobIntentionAndIdentityBeforeHistoryPages() throws Exception {
    String cover = "张三求职材料\n求职意向：电气维修方向\n居住地点：山西大同\n联系方式：15300000000\n可到岗时间：两周以内";
    var value = adapter((page, image) -> Optional.of(cover),
        (page, image) -> { throw new AssertionError("readable cover must not require vision"); },
        new IntegrationProperties()).extract("cover.pdf", new ByteArrayInputStream(pdfWithVisibleMarks(1)));
    assertThat(value.pages().getFirst().text()).isEqualTo(cover);
    assertThat(value.pages().getFirst().route()).isEqualTo("OCR_TEXT");
  }

  private static final int CONFIGURED_MAXIMUM_EXTRACTED_CHARACTERS = 500_000;
  private static final String OVERSIZED_EXTRACTED_TEXT = oversizedExtractedText();
  private static final String HIGHLY_COMPRESSED_TEXT = "教育经历项目经历技能".repeat(20_000);

  @Test
  void canonicalizesLineEndingsBeforePersistingEvidenceText() throws Exception {
    String recognized = "教育经历：计算机本科，项目经历：服务器维修。\r\n"
        + "技能：Linux 与 GPU 故障排查，逐字保存📄原文。\r"
        + "电话未提供，不得猜测；职责包括设备巡检与工单处理。";
    var value = adapter((page, image) -> Optional.of(recognized), (page, image) -> Optional.empty());
    var result = value.extract("scan.pdf", new ByteArrayInputStream(blankPdf()));
    assertThat(result.pages().getFirst().text())
        .isEqualTo(recognized.replace("\r\n", "\n").replace('\r', '\n'))
        .doesNotContain("\r");
    assertThat(result.extractorVersion()).endsWith("-v23");
  }

  @Test
  void removesPdfFormattingNoiseWithoutChangingResumeFacts() throws Exception {
    String noisy = "\uFEFF姓\u200B名：张三\u00A0\u00A0C++ / C# / .NET\r\n"
        + "邮箱：zhang.san+job@example.com\u200E\t手机：+86 138-0000-0000\f"
        + "任职：2023-06—至今\u2028期望薪资：¥25,000/月\n\n\n\n项目经历：支付平台";
    String expected = "姓名：张三 C++ / C# / .NET\n"
        + "邮箱：zhang.san+job@example.com 手机：+86 138-0000-0000\n"
        + "任职：2023-06—至今\n期望薪资：¥25,000/月\n\n项目经历：支付平台";

    var value = adapter((page, image) -> Optional.of(noisy), (page, image) -> Optional.empty());
    var result = value.extract("张三_C++工程师_2023-06.pdf", new ByteArrayInputStream(blankPdf()));

    assertThat(result.pages().getFirst().text()).isEqualTo(expected);
  }

  @Test
  void replacementAndPrivateUseGarbageStillFailsThePdfQualityGate() throws Exception {
    String garbage = "教育经历 项目经历 技能 Java " + "\uFFFD\uE000".repeat(100);
    String recovered = "教育经历：计算机本科；项目经历：支付平台；技能：Java、Spring Boot；手机：13800000000。";
    var value = adapter((page, image) -> Optional.of(garbage), (page, image) -> Optional.of(recovered));

    var result = value.extract("乱码简历.pdf", new ByteArrayInputStream(blankPdf()));

    assertThat(result.route()).isEqualTo("MULTIMODAL");
    assertThat(result.pages().getFirst().text()).isEqualTo(recovered);
  }

  @Test
  void preservesShortCertificateContinuationWithoutRequiringRepeatedResumeHeadings() throws Exception {
    String first = "教育经历：计算机本科；项目经历：服务器运维；技能 Linux，五年工作经历，电话未提供。";
    String certificate = "网络工程师 阿里云ACP（云计算高级工程师）";
    var value = adapter((page, image) -> Optional.of(page == 1 ? first : certificate),
        (page, image) -> { throw new AssertionError("valid short continuation must not call vision"); });
    var result = value.extract("two-pages.pdf", new ByteArrayInputStream(pdfWithVisibleMarks(2)));
    assertThat(result.pages()).hasSize(2);
    assertThat(result.pages().get(1).text()).isEqualTo(certificate);
    assertThat(result.coverageRatio()).isEqualTo(1);
  }

  @Test
  void treatsUnrecognizedSparseDecorationAsBlankContinuationButStillRejectsWeakFirstPage()
      throws Exception {
    String first = "教育经历：计算机本科；项目经历：服务器运维；技能 Linux，五年工作经历，电话未提供。";
    var value = adapter((page, image) -> page == 1 ? Optional.of(first) : Optional.empty(),
        (page, image) -> Optional.empty());
    var result = value.extract("watermark.pdf", new ByteArrayInputStream(pdfWithSparseNoiseTail(10)));
    assertThat(result.pages()).hasSize(2);
    assertThat(result.pages().get(1).route()).isEqualTo("BLANK_PAGE");
    assertThat(result.pages().get(1).text()).isEmpty();
    var shortFirst = adapter((page, image) -> Optional.of("网络工程师 阿里云ACP"), (page, image) -> Optional.empty());
    assertThatThrownBy(() -> shortFirst.extract("short.pdf", new ByteArrayInputStream(blankPdf())))
        .hasMessage("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:1");
  }

  @Test
  void doesNotHideUnreadableContinuationThatSpansTheContentArea() throws Exception {
    String first = "教育经历：计算机本科；项目经历：服务器运维；技能 Linux，五年工作经历，电话未提供。";
    var value = adapter((page, image) -> page == 1 ? Optional.of(first) : Optional.empty(),
        (page, image) -> Optional.empty());

    assertThatThrownBy(() -> value.extract("wide-mark.pdf",
        new ByteArrayInputStream(pdfWithSparseNoiseTail(100))))
        .hasMessage("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:2");
  }

  @Test
  void treatsTemplateHeaderOnlyContinuationAsBlank() throws Exception {
    String first = "教育经历：计算机本科；项目经历：服务器运维；技能 Linux，五年工作经历，电话未提供。";
    var value = adapter((page, image) -> page == 1 ? Optional.of(first) : Optional.empty(),
        (page, image) -> Optional.empty());

    var result = value.extract("template-tail.pdf",
        new ByteArrayInputStream(pdfWithTemplateHeaderTail()));

    assertThat(result.pages().get(1).route()).isEqualTo("BLANK_PAGE");
    assertThat(result.pages().get(1).text()).isEmpty();
  }

  @Test
  void treatsPageEdgeFrameOnlyContinuationAsBlank() throws Exception {
    String first = "教育经历：计算机本科；项目经历：服务器运维；技能 Linux，五年工作经历，电话未提供。";
    var value = adapter((page, image) -> page == 1 ? Optional.of(first) : Optional.empty(),
        (page, image) -> Optional.empty());

    var result = value.extract("edge-frame-tail.pdf",
        new ByteArrayInputStream(pdfWithPageEdgeFrameTail()));

    assertThat(result.pages().get(1).route()).isEqualTo("BLANK_PAGE");
    assertThat(result.pages().get(1).text()).isEmpty();
  }

  @Test
  void preservesVerifiedBlankContinuationAsAnEmptyPhysicalPageWithoutCallingExternalModels() throws Exception {
    String first = "教育经历：计算机本科；项目经历：服务器运维；技能 Linux，五年工作经历，电话未提供。";
    var value = adapter((page, image) -> {
      assertThat(page).isEqualTo(1);
      return Optional.of(first);
    }, (page, image) -> {throw new AssertionError("verified empty continuation needs no model");});
    var result = value.extract("blank-tail.pdf", new ByteArrayInputStream(pdfWithPages(2)));
    assertThat(result.pageCount()).isEqualTo(2);
    assertThat(result.successfulPages()).isEqualTo(2);
    assertThat(result.coverageRatio()).isEqualTo(1);
    assertThat(result.route()).isEqualTo("OCR_TEXT");
    assertThat(result.pages().get(1).route()).isEqualTo("BLANK_PAGE");
    assertThat(result.pages().get(1).text()).isEmpty();
    assertThat(result.pages().get(1).pageNo()).isEqualTo(2);
  }

  private static byte[] pdfWithVisibleMarks(int count) throws Exception {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      for (int index = 0; index < count; index++) {
        var page = new PDPage(); document.addPage(page);
        try (var content = new PDPageContentStream(document, page)) {
          content.addRect(30, 30, 10, 10); content.fill();
        }
      }
      document.save(output); return output.toByteArray();
    }
  }

  private static byte[] pdfWithSparseNoiseTail(float markWidth) throws Exception {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var first = new PDPage();
      document.addPage(first);
      try (var content = new PDPageContentStream(document, first)) {
        content.addRect(30, 30, 10, 10);
        content.fill();
      }
      var tail = new PDPage();
      document.addPage(tail);
      try (var content = new PDPageContentStream(document, tail)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawPaintedText(content, "c51f09ec3-4HNy0-fO-dW-OKhlvb-tracking-noise", 0.00784f, 0,
            RenderingMode.FILL, 100);
        content.addRect(30, 700, markWidth, 10);
        content.fill();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithTemplateHeaderTail() throws Exception {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var first = new PDPage();
      document.addPage(first);
      try (var content = new PDPageContentStream(document, first)) {
        content.addRect(30, 30, 10, 10);
        content.fill();
      }
      var tail = new PDPage();
      document.addPage(tail);
      try (var content = new PDPageContentStream(document, tail)) {
        content.setNonStrokingColor(0.4f, 0.5f, 0.6f);
        // 模板横条底部略低于旧 6% 页边距阈值，模拟真实第三页的装饰页眉。
        content.addRect(0, 733, tail.getMediaBox().getWidth(), 22);
        content.fill();
        content.setNonStrokingColor(1f, 1f, 1f);
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        drawLine(content, 40, 741, "PERSONAL RESUME");
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithPageEdgeFrameTail() throws Exception {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var first = new PDPage();
      document.addPage(first);
      try (var content = new PDPageContentStream(document, first)) {
        content.addRect(30, 30, 10, 10);
        content.fill();
      }
      var tail = new PDPage(new PDRectangle(1_264, 1_788));
      document.addPage(tail);
      try (var content = new PDPageContentStream(document, tail)) {
        content.setStrokingColor(0.75f, 0.75f, 0.75f);
        content.setLineWidth(1);
        content.addRect(0, 0, tail.getMediaBox().getWidth(), tail.getMediaBox().getHeight());
        content.stroke();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  @Test
  void extractsDocxNativelyWithQualityMetadata() throws Exception {
    byte[] document;
    try (XWPFDocument value = new XWPFDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      value
          .createParagraph()
          .createRun()
          .setText("Java 工程师，五年项目经历，技能包括 Spring Boot 与 MySQL，教育经历为计算机本科。");
      value.write(output);
      document = output.toByteArray();
    }
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter((page, image) -> Optional.empty(), (page, image) -> Optional.empty());

    ExtractedDocument result =
        adapter.extract(
            "resume.docx",
            new ByteArrayInputStream(document));

    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages())
        .singleElement()
        .satisfies(
            page -> {
              assertThat(page.text()).contains("Java", "项目经历", "技能");
              assertThat(page.contentHash()).hasSize(64);
            });
  }

  @Test
  void routesLowQualityPdfPageThroughOcrBeforeMultimodal() throws Exception {
    byte[] document = blankPdf();
    String recognized = "OCR 教育经历、项目经历、技能 Java Spring Boot，电话 13800000000，内容足够用于质量校验。";
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter(
            (page, image) -> Optional.of(recognized),
            (page, image) -> {
              throw new AssertionError("multimodal must not run after valid OCR");
            });

    ExtractedDocument result =
        adapter.extract("scan.pdf", new ByteArrayInputStream(document));

    assertThat(result.route()).isEqualTo("OCR_TEXT");
    assertThat(result.fallbackPages()).isEqualTo(1);
    assertThat(result.pages().getFirst().text()).isEqualTo(recognized);
  }

  @Test
  void fallsBackToMultimodalWhenOcrIsStillLowQuality() throws Exception {
    byte[] document = blankPdf();
    String recognized = "多模态识别：教育经历、工作经历、项目经历与技能 Java，手机 13800000000，文本满足质量阈值。";
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter((page, image) -> Optional.of("x"), (page, image) -> Optional.of(recognized));

    ExtractedDocument result =
        adapter.extract("scan.pdf", new ByteArrayInputStream(document));

    assertThat(result.route()).isEqualTo("MULTIMODAL");
    assertThat(result.pages().getFirst().text()).isEqualTo(recognized);
  }

  @Test
  void readsRasterIdentityEvenWhenNativeFirstPageTextPassesQualityGate() throws Exception {
    byte[] document = pdfWithRasterIdentityAndNativeHistory();
    String visual = "Synthetic Candidate. Education, experience, project and skills: Java. Contact available.";
    var adapter = adapter((page, image) -> Optional.empty(),
        (page, image) -> Optional.of(visual));

    ExtractedDocument result = adapter.extract("mixed.pdf", new ByteArrayInputStream(document));

    assertThat(result.route()).isEqualTo("MULTIMODAL");
    assertThat(result.fallbackPages()).isEqualTo(1);
    assertThat(result.pages().getFirst().text())
        .contains("Synthetic Candidate", "Education Experience Project Skills Java");
  }

  @Test
  void supplementsNativeFirstPageWhenVisibleFactsAreManySmallRasterFragments() throws Exception {
    byte[] document = pdfWithFragmentedRasterFactsAndNativeHistory();
    String visual = "姓名：示例候选人；手机：13800000000；邮箱：example@example.com；教育经历：本科；工作经历：服务器售后维修；技能：设备维护。";
    var adapter = adapter((page, image) -> Optional.of(visual),
        (page, image) -> { throw new AssertionError("valid OCR needs no vision"); });

    ExtractedDocument result = adapter.extract("fragmented-facts.pdf", new ByteArrayInputStream(document));

    assertThat(result.route()).isEqualTo("OCR_TEXT");
    assertThat(result.fallbackPages()).isEqualTo(1);
    assertThat(result.pages().getFirst().text())
        .contains("13800000000", "example@example.com", "Education Experience Project Skills");
  }

  @Test
  void keepsGoodNativeTextWhenOptionalRasterRecognitionIsUnavailable() throws Exception {
    byte[] document = pdfWithRasterIdentityAndNativeHistory();
    boolean[] attempted = {false};
    var adapter = adapter((page, image) -> {
      attempted[0] = true;
      return Optional.empty();
    }, (page, image) -> { throw new IllegalStateException("visual service unavailable"); });

    ExtractedDocument result = adapter.extract("mixed.pdf", new ByteArrayInputStream(document));

    assertThat(attempted[0]).isTrue();
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().text()).contains("Education", "Experience");
  }

  @Test
  void smallRasterPhotoDoesNotTriggerOptionalVisualPass() throws Exception {
    var adapter = adapter((page, image) -> { throw new AssertionError("small photo needs no OCR"); },
        (page, image) -> { throw new AssertionError("small photo needs no vision"); });

    ExtractedDocument result = adapter.extract("photo.pdf",
        new ByteArrayInputStream(pdfWithRasterIdentityAndNativeHistory(false)));

    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
  }

  private static byte[] pdfWithRasterIdentityAndNativeHistory() throws Exception {
    return pdfWithRasterIdentityAndNativeHistory(true);
  }

  private static byte[] pdfWithRasterIdentityAndNativeHistory(boolean largeRaster) throws Exception {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      var image = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
      var graphics = image.createGraphics();
      try {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.drawString("Synthetic Candidate", 200, 80);
      } finally {
        graphics.dispose();
      }
      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(LosslessFactory.createFromImage(document, image), 0, 0,
            largeRaster ? page.getMediaBox().getWidth() : 80,
            largeRaster ? page.getMediaBox().getHeight() : 80);
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawLine(content, 40, 700,
            "Education Experience Project Skills Java and server operations contact details");
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithFragmentedRasterFactsAndNativeHistory() throws Exception {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      var fragment = new BufferedImage(160, 20, BufferedImage.TYPE_INT_RGB);
      var graphics = fragment.createGraphics();
      try {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, fragment.getWidth(), fragment.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.drawString("Candidate facts", 8, 15);
      } finally {
        graphics.dispose();
      }
      var image = LosslessFactory.createFromImage(document, fragment);
      try (var content = new PDPageContentStream(document, page)) {
        for (int row = 0; row < 8; row++) {
          for (int column = 0; column < 3; column++) {
            content.drawImage(image, 40 + column * 180, 650 - row * 55, 160, 20);
          }
        }
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawLine(content, 40, 750,
            "Education Experience Project Skills Java and server operations contact details");
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  @Test
  void routesOtherwiseReadablePdfThroughOcrWhenAnyGlyphHasNoUnicodeMapping() throws Exception {
    String recovered = "教育经历：计算机本科；项目经历：支付平台；技能：Java、Spring Boot；手机：13800000000。";
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter(
            (page, image) -> Optional.of(recovered),
            (page, image) -> {
              throw new AssertionError("valid OCR must avoid multimodal fallback");
            });

    ExtractedDocument result = adapter.extract(
        "missing-unicode-map.pdf", new ByteArrayInputStream(pdfWithReadableTextAndUnmappedCid()));

    assertThat(result.route()).isEqualTo("OCR_TEXT");
    assertThat(result.pages().getFirst().text()).isEqualTo(recovered);
  }

  @Test
  void clearsMissingUnicodeMappingStateBeforeExtractingTheNextPage() throws Exception {
    String recovered = "教育经历：计算机本科；项目经历：支付平台；技能：Java、Spring Boot；手机：13800000000。";
    LayeredResumeDocumentExtractionAdapter adapter = adapter(
        (page, image) -> {
          assertThat(page).isEqualTo(1);
          return Optional.of(recovered);
        },
        (page, image) -> {
          throw new AssertionError("neither page needs multimodal fallback");
        });

    ExtractedDocument result = adapter.extract("two-pages.pdf",
        new ByteArrayInputStream(pdfWithReadableTextAndUnmappedCid(false, true)));

    assertThat(result.pages()).extracting(ExtractedPage::route)
        .containsExactly("OCR_TEXT", "NATIVE_TEXT");
  }

  @Test
  void ignoresTransparentDiagonalUnmappedWatermarkBeforePdfBoxRequestsUnicode() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    LayeredResumeDocumentExtractionAdapter adapter = adapter(ocr, multimodal);
    Logger pdfFontLogger = (Logger) LoggerFactory.getLogger("org.apache.pdfbox.pdmodel.font.PDType0Font");
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    pdfFontLogger.addAppender(captured);
    ExtractedDocument result;
    try {
      result = adapter.extract("watermarked.pdf",
          new ByteArrayInputStream(pdfWithReadableTextAndUnmappedCid(true)));
    } finally {
      pdfFontLogger.detachAppender(captured);
      captured.stop();
    }

    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(captured.list).noneMatch(event -> event.getFormattedMessage()
        .contains("No Unicode mapping"));
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsOverlappingGlyphsBeforePdfBoxDeduplicatesTheOutput() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        for (int repeat = 0; repeat < 3; repeat++) {
          drawLine(content, 40, 700, "Resume education experience skills Java phone 13800000000");
        }
      }
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(80, 67_108_864L, 4_096));

    assertThatThrownBy(() -> adapter.extract("overlapping.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_TEXT_LIMIT:1");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void acceptsComplexPdfResourcesBeyondTheDocxEntryBudget() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      var resources = new COSArray();
      for (int index = 0; index < 5_000; index++) {
        resources.add(new COSDictionary());
      }
      page.getCOSObject().setItem(COSName.getPDFName("SyntheticResources"), resources);
      try (var content = new PDPageContentStream(document, page)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawLine(content, 40, 700, "Resume education experience skills Java phone 13800000000");
      }
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumContainerEntries(1);
    var extracted = adapter(ocr, multimodal, configuration)
        .extract("complex-resources.pdf", new ByteArrayInputStream(bytes));

    assertThat(extracted.route()).isEqualTo("NATIVE_TEXT");
    configuration.getDocumentExtraction().setMaximumPdfResourceNodes(4_096);
    var bounded = adapter(ocr, multimodal, configuration);
    assertThatThrownBy(() -> bounded.extract("complex-resources.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void acceptsHighResolutionSourceScanWhileKeepingRenderedPageWithinItsOwnBudget() throws Exception {
    byte[] bytes;
    var source = new BufferedImage(2_480, 3_508, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(JPEGFactory.createFromImage(document, source), 0, 0,
            page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
      }
      document.save(output);
      bytes = output.toByteArray();
    } finally {
      source.flush();
    }
    String recognized = "教育经历：计算机本科；项目经历：服务开发；技能：Java；手机：13800000000。";
    ResumeOcrPort ocr = (page, image) -> {
      try {
        var rendered = ImageIO.read(new ByteArrayInputStream(image));
        assertThat((long) rendered.getWidth() * rendered.getHeight()).isLessThanOrEqualTo(8_000_000L);
      } catch (IOException error) {
        throw new AssertionError(error);
      }
      return Optional.of(recognized);
    };
    var extracted = adapter(ocr, (page, image) -> Optional.empty())
        .extract("high-resolution-scan.pdf", new ByteArrayInputStream(bytes));

    assertThat(extracted.pages().getFirst().text()).isEqualTo(recognized);
  }

  @Test
  void rejectsCompressedPdfContentBeforeUnboundedPdfBoxDecoding() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      String content = "% bounded synthetic comment\n".repeat(40);
      page.setContents(new PDStream(document,
          new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE));
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 128L, 4_096));

    assertThatThrownBy(() -> adapter.extract("expanded.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsLargeSourceImageOnSmallPageBeforeRenderingOrOcr() throws Exception {
    byte[] bytes = pdfWithLargeSourceImage(false);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var adapter = adapter(ocr, multimodal, configuration);

    assertThatThrownBy(() -> adapter.extract("source-image.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:1");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void preservesNativeTextWhenOptionalVisualSupplementExceedsImageLimit() throws Exception {
    byte[] bytes = pdfWithLargeSourceImage(true);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var adapter = adapter(ocr, multimodal, configuration);

    var result = adapter.extract("native-and-image.pdf", new ByteArrayInputStream(bytes));
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().route()).isEqualTo("NATIVE_TEXT_VISUAL_LIMIT");
    assertThat(result.pages().getFirst().text()).contains(NATIVE_RESUME_LINES);
    assertThat(result.successfulPages()).isEqualTo(1);
    assertThat(result.coverageRatio()).isEqualTo(1);
    assertThat(result.fallbackPages()).isZero();
    verifyNoInteractions(ocr, multimodal);
  }

  private static final String[] NATIVE_RESUME_LINES = {
      "Resume education experience project skills Java phone 13800000000",
      "Education: computer science. Experience: platform support and server operations.",
      "Project skills: Java services, Linux maintenance, network troubleshooting."
  };

  @Test
  void acceptsSyntheticNativeResumeFixtureWithoutVisualRecognition() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        drawNativeResume(content);
      }
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMinimumPageCharacters(100);
    var result = adapter(ocr, multimodal, configuration)
        .extract("native-control.pdf", new ByteArrayInputStream(bytes));
    assertThat(result.pages().getFirst().text()).contains(NATIVE_RESUME_LINES).hasSizeGreaterThan(100);
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.coverageRatio()).isEqualTo(1);
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void extractsNativeTextWithoutDecodingUndrawnOversizedImageResource() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties(500_000, 8_192L, 4_096);
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var result = adapter(ocr, multimodal, configuration).extract("undrawn-image.pdf",
        new ByteArrayInputStream(pdfWithNativeAndSharedImage(false, false)));
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().text()).contains(NATIVE_RESUME_LINES);
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void optionalImageLimitCannotHideTheSameImageFromRequiredLaterPageRecognition() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var adapter = adapter(ocr, multimodal, configuration);
    assertThatThrownBy(() -> adapter.extract("shared-oversized-image.pdf",
        new ByteArrayInputStream(pdfWithNativeAndSharedImage(true, true))))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:2");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void preservesNativeTextWhenOptionalVisualSupplementExceedsDocumentBudget() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var result = adapter(ocr, multimodal, properties(500_000, 500_000L, 4_096))
        .extract("optional-image-budget.pdf", new ByteArrayInputStream(pdfWithNativeAndSharedImage(true, false)));
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().route()).isEqualTo("NATIVE_TEXT_VISUAL_LIMIT");
    assertThat(result.pages().getFirst().text()).contains(NATIVE_RESUME_LINES);
    assertThat(result.successfulPages()).isEqualTo(1);
    assertThat(result.fallbackPages()).isZero();
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void optionalImageReservationFailureCannotBypassTheDocumentBudgetOnLaterPage() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 500_000L, 4_096));
    assertThatThrownBy(() -> adapter.extract("shared-image-budget.pdf",
        new ByteArrayInputStream(pdfWithNativeAndSharedImage(true, true))))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  private static byte[] pdfWithNativeAndSharedImage(boolean drawFirstImage, boolean addScanTail)
      throws Exception {
    var raster = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var image = LosslessFactory.createFromImage(document, raster);
      var first = new PDPage(new PDRectangle(100, 100));
      first.setResources(new PDResources());
      first.getResources().add(image);
      document.addPage(first);
      try (var content = new PDPageContentStream(document, first)) {
        if (drawFirstImage) content.drawImage(image, 0, 0, 100, 100);
        drawNativeResume(content);
      }
      if (addScanTail) {
        var tail = new PDPage(new PDRectangle(100, 100));
        document.addPage(tail);
        try (var content = new PDPageContentStream(document, tail)) {
          content.drawImage(image, 0, 0, 100, 100);
        }
      }
      document.save(output);
      return output.toByteArray();
    } finally {
      raster.flush();
    }
  }

  private static void drawNativeResume(PDPageContentStream content) throws Exception {
    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 1);
    for (int index = 0; index < NATIVE_RESUME_LINES.length; index++) {
      drawLine(content, 1, 90 - index * 4, NATIVE_RESUME_LINES[index]);
    }
  }

  @Test
  void extractsNativeTextInsideContinuationFormWithoutDecodingItsOversizedImage() throws Exception {
    byte[] bytes;
    var raster = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var first = new PDPage(new PDRectangle(100, 100));
      document.addPage(first);
      try (var content = new PDPageContentStream(document, first)) {
        drawNativeResume(content);
      }
      var tail = new PDPage(new PDRectangle(100, 100));
      document.addPage(tail);
      var form = new PDFormXObject(document);
      form.setResources(new PDResources());
      form.setBBox(new PDRectangle(100, 100));
      try (var content = new PDFormContentStream(form)) {
        content.drawImage(LosslessFactory.createFromImage(document, raster), 0, 0, 100, 100);
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 1);
        content.beginText();
        content.newLineAtOffset(1, 90);
        content.showText("Experience project skills: continuation responsibilities and server maintenance.");
        content.endText();
      }
      try (var content = new PDPageContentStream(document, tail)) {
        content.drawForm(form);
      }
      document.save(output);
      bytes = output.toByteArray();
    } finally {
      raster.flush();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var result = adapter(ocr, multimodal, configuration)
        .extract("native-form-image.pdf", new ByteArrayInputStream(bytes));
    assertThat(result.pageCount()).isEqualTo(2);
    assertThat(result.successfulPages()).isEqualTo(2);
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().get(1).text()).contains("continuation responsibilities");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void extractsNativeTextWithoutDecodingSmallVisibleInlineImageWithLargeSourceDimensions() throws Exception {
    String content = "q 1 0 0 1 0 0 cm BI /W 400 /H 400 /BPC 8 /CS /G ID "
        + "x".repeat(160_000) + "\nEI Q\n" + nativeResumeContent();
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var result = adapter(ocr, multimodal, configuration).extract("native-inline-image.pdf",
        new ByteArrayInputStream(pdfWithNativeRawContent(content)));
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().text()).contains(NATIVE_RESUME_LINES);
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsImageDisguisedToUnicodeBeforeNativeExtraction() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 128L, 4_096));
    assertThatThrownBy(() -> adapter.extract("disguised-unicode.pdf",
        new ByteArrayInputStream(pdfWithImageDisguisedTextDependency(COSName.TO_UNICODE))))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_INVALID");
    verifyNoInteractions(ocr, multimodal);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Resources", "XObject", "FontDescriptor", "Font", "FontValue", "DescendantFont",
      "CharProcs", "CharProcValue", "ExtGState", "ExtGStateValue", "ExtGStateFont", "Pattern", "PatternValue"})
  void rejectsImageDisguisedTextResourcesBeforeNativeExtraction(String role) throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 128L, 4_096));
    assertThatThrownBy(() -> adapter.extract("disguised-text-container.pdf",
        new ByteArrayInputStream(pdfWithImageDisguisedTextDependency(COSName.getPDFName(role)))))
        .as("text resource %s must not hide its decoded child", role)
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_INVALID");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsImageDisguisedContentsEvenWhenTheSameStreamIsAnImageResource() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 128L, 4_096));
    assertThatThrownBy(() -> adapter.extract("disguised-shared-contents.pdf",
        new ByteArrayInputStream(pdfWithImageDisguisedTextDependency(COSName.CONTENTS))))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_INVALID");
    verifyNoInteractions(ocr, multimodal);
  }

  private static byte[] pdfWithImageDisguisedTextDependency(COSName disguisedRole) throws Exception {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      var resources = new PDResources();
      var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      resources.put(COSName.getPDFName("F1"), font);
      page.setResources(resources);
      var expanded = new PDStream(document,
          new ByteArrayInputStream("% oversized synthetic text dependency\n".repeat(100)
              .getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE);
      var content = new PDStream(document,
          new ByteArrayInputStream("BT /F1 1 Tf 1 90 Td (Resume skills) Tj ET\n"
              .getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE);
      page.setContents(content);
      if (COSName.CONTENTS.equals(disguisedRole)) {
        expanded.getCOSObject().setItem(COSName.SUBTYPE, COSName.IMAGE);
        page.setContents(expanded);
        var xobjects = new COSDictionary();
        xobjects.setItem(COSName.getPDFName("SharedImage"), expanded);
        resources.getCOSObject().setItem(COSName.XOBJECT, xobjects);
      } else if (COSName.XOBJECT.equals(disguisedRole)) {
        var form = new PDFormXObject(expanded);
        form.setResources(new PDResources());
        form.setBBox(new PDRectangle(100, 100));
        var xobjects = new COSDictionary();
        xobjects.setItem(COSName.getPDFName("Fm1"), form);
        resources.getCOSObject().setItem(COSName.XOBJECT, imageDisguisedDictionary(document, xobjects));
        page.setContents(new PDStream(document,
            new ByteArrayInputStream("/Fm1 Do\n".getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE));
      } else if (COSName.FONT_DESC.equals(disguisedRole)) {
        var descriptor = new PDStream(document).getCOSObject();
        descriptor.setItem(COSName.SUBTYPE, COSName.IMAGE);
        descriptor.setItem(COSName.FONT_FILE, expanded);
        font.getCOSObject().setItem(COSName.FONT_DESC, descriptor);
      } else if (COSName.CHAR_PROCS.equals(disguisedRole) || "CharProcValue".equals(disguisedRole.getName())) {
        var fontDictionary = font.getCOSObject();
        fontDictionary.setItem(COSName.SUBTYPE, COSName.getPDFName("Type3"));
        fontDictionary.setItem(COSName.FONT_BBOX, new PDRectangle(100, 100));
        var matrix = new COSArray();
        matrix.setFloatArray(new float[] {0.001f, 0, 0, 0.001f, 0, 0});
        fontDictionary.setItem(COSName.FONT_MATRIX, matrix);
        fontDictionary.setInt(COSName.FIRST_CHAR, 65);
        fontDictionary.setInt(COSName.LAST_CHAR, 65);
        var widths = new COSArray();
        widths.add(COSInteger.get(500));
        fontDictionary.setItem(COSName.WIDTHS, widths);
        var encoding = new COSDictionary();
        var differences = new COSArray();
        differences.add(COSInteger.get(65));
        differences.add(COSName.getPDFName("A"));
        encoding.setItem(COSName.DIFFERENCES, differences);
        fontDictionary.setItem(COSName.ENCODING, encoding);
        var glyphs = new COSDictionary();
        if (COSName.CHAR_PROCS.equals(disguisedRole)) {
          glyphs.setItem(COSName.getPDFName("A"), expanded);
          fontDictionary.setItem(COSName.CHAR_PROCS, imageDisguisedDictionary(document, glyphs));
        } else {
          expanded.getCOSObject().setItem(COSName.SUBTYPE, COSName.IMAGE);
          glyphs.setItem(COSName.getPDFName("A"), expanded);
          fontDictionary.setItem(COSName.CHAR_PROCS, glyphs);
        }
      } else if ("DescendantFont".equals(disguisedRole.getName())) {
        var descendant = new COSDictionary();
        descendant.setItem(COSName.TO_UNICODE, expanded);
        var descendants = new COSArray();
        descendants.add(imageDisguisedDictionary(document, descendant));
        font.getCOSObject().setItem(COSName.DESCENDANT_FONTS, descendants);
      } else {
        font.getCOSObject().setItem(COSName.TO_UNICODE, expanded);
        if (COSName.TO_UNICODE.equals(disguisedRole)) {
          expanded.getCOSObject().setItem(COSName.SUBTYPE, COSName.IMAGE);
        } else if (COSName.RESOURCES.equals(disguisedRole)) {
          page.setResources(new PDResources(imageDisguisedDictionary(document, resources.getCOSObject())));
        } else if (COSName.FONT.equals(disguisedRole)) {
          var fonts = (COSDictionary) resources.getCOSObject().getDictionaryObject(COSName.FONT);
          resources.getCOSObject().setItem(COSName.FONT, imageDisguisedDictionary(document, fonts));
        } else if ("FontValue".equals(disguisedRole.getName())) {
          var fonts = (COSDictionary) resources.getCOSObject().getDictionaryObject(COSName.FONT);
          fonts.setItem(COSName.getPDFName("F1"), imageDisguisedDictionary(document, font.getCOSObject()));
        } else if (disguisedRole.getName().startsWith("ExtGState")) {
          var setting = new COSArray();
          setting.add("ExtGStateFont".equals(disguisedRole.getName())
              ? imageDisguisedDictionary(document, font.getCOSObject()) : font.getCOSObject());
          setting.add(COSInteger.ONE);
          var state = new COSDictionary();
          state.setItem(COSName.FONT, setting);
          var states = new COSDictionary();
          states.setItem(COSName.getPDFName("GS1"), "ExtGStateValue".equals(disguisedRole.getName())
              ? imageDisguisedDictionary(document, state) : state);
          resources.getCOSObject().removeItem(COSName.FONT);
          resources.getCOSObject().setItem(COSName.EXT_G_STATE, COSName.EXT_G_STATE.equals(disguisedRole)
              ? imageDisguisedDictionary(document, states) : states);
          page.setContents(new PDStream(document,
              new ByteArrayInputStream("/GS1 gs BT 1 90 Td (Resume skills) Tj ET\n"
                  .getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE));
        } else if (disguisedRole.getName().startsWith("Pattern")) {
          var pattern = new PDStream(document).getCOSObject();
          pattern.setInt(COSName.PATTERN_TYPE, 1);
          var patternResources = new COSDictionary();
          patternResources.setItem(COSName.FONT, resources.getCOSObject().getDictionaryObject(COSName.FONT));
          pattern.setItem(COSName.RESOURCES, patternResources);
          var patterns = new COSDictionary();
          patterns.setItem(COSName.getPDFName("P1"), "PatternValue".equals(disguisedRole.getName())
              ? imageDisguisedDictionary(document, pattern) : pattern);
          resources.getCOSObject().removeItem(COSName.FONT);
          resources.getCOSObject().setItem(COSName.PATTERN, COSName.PATTERN.equals(disguisedRole)
              ? imageDisguisedDictionary(document, patterns) : patterns);
        } else {
          throw new IllegalArgumentException("unsupported disguised role " + disguisedRole.getName());
        }
      }
      document.addPage(page);
      document.save(output);
      return output.toByteArray();
    }
  }

  private static COSStream imageDisguisedDictionary(PDDocument document, COSDictionary dictionary) {
    var stream = new PDStream(document).getCOSObject();
    for (var entry : dictionary.entrySet()) stream.setItem(entry.getKey(), entry.getValue());
    stream.setItem(COSName.SUBTYPE, COSName.IMAGE);
    return stream;
  }

  @Test
  void extractsNativeTextWithThousandsOfType3GlyphResources() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var result = adapter(ocr, multimodal).extract("fragmented-fonts.pdf",
        new ByteArrayInputStream(pdfWithFragmentedFonts(300)));
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    assertThat(result.pages().getFirst().text()).contains("skills", "Experience");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void countsRepeatedPdfResourceReferencesOnlyOnce() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      page.setResources(new PDResources());
      page.getResources().put(COSName.getPDFName("F1"),
          new PDType1Font(Standard14Fonts.FontName.HELVETICA));
      page.setContents(new PDStream(document,
          new ByteArrayInputStream(nativeResumeContent().getBytes(StandardCharsets.US_ASCII))));
      var shared = new COSDictionary();
      var sharedReference = new COSObject(shared);
      shared.setItem(COSName.getPDFName("Self"), sharedReference);
      var references = new COSArray();
      for (int group = 0; group < 32; group++) {
        var aliases = new COSArray();
        for (int index = 0; index < 128; index++) aliases.add(sharedReference);
        references.add(aliases);
      }
      page.getCOSObject().setItem(COSName.getPDFName("SharedResources"), references);
      document.addPage(page);
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumPdfResourceNodes(128);
    var result = adapter(ocr, multimodal, configuration)
        .extract("shared-resources.pdf", new ByteArrayInputStream(bytes));
    assertThat(result.route()).isEqualTo("NATIVE_TEXT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void stillRejectsPdfResourceNodeFloodBeforeNativeExtractionOrRecognition() throws Exception {
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumPdfResourceNodes(128);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var value = adapter(ocr, multimodal, configuration);
    byte[] bytes = pdfWithFragmentedFonts(300);
    assertThatThrownBy(() -> value.extract("resource-flood.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  private static byte[] pdfWithFragmentedFonts(int fontCount) throws Exception {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      page.setResources(new PDResources());
      page.getResources().put(COSName.getPDFName("F1"),
          new PDType1Font(Standard14Fonts.FontName.HELVETICA));
      page.setContents(new PDStream(document,
          new ByteArrayInputStream(nativeResumeContent().getBytes(StandardCharsets.US_ASCII))));
      var fonts = (COSDictionary) page.getResources().getCOSObject().getDictionaryObject(COSName.FONT);
      for (int index = 0; index < fontCount; index++) {
        var font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.getPDFName("Type3"));
        font.setItem(COSName.FONT_BBOX, new PDRectangle(500, 500));
        var matrix = new COSArray();
        matrix.setFloatArray(new float[] {0.001f, 0, 0, 0.001f, 0, 0});
        font.setItem(COSName.FONT_MATRIX, matrix);
        font.setInt(COSName.FIRST_CHAR, 65);
        font.setInt(COSName.LAST_CHAR, 76);
        var widths = new COSArray();
        var differences = new COSArray();
        differences.add(COSInteger.get(65));
        var glyphs = new COSDictionary();
        for (int glyph = 0; glyph < 12; glyph++) {
          var name = COSName.getPDFName(String.valueOf((char) ('A' + glyph)));
          widths.add(COSInteger.get(500));
          differences.add(name);
          glyphs.setItem(name, new PDStream(document,
              new ByteArrayInputStream("500 0 d0\n".getBytes(StandardCharsets.US_ASCII))));
        }
        var encoding = new COSDictionary();
        encoding.setItem(COSName.DIFFERENCES, differences);
        font.setItem(COSName.ENCODING, encoding);
        font.setItem(COSName.WIDTHS, widths);
        font.setItem(COSName.CHAR_PROCS, glyphs);
        fonts.setItem(COSName.getPDFName("T" + index), font);
      }
      document.addPage(page);
      document.save(output);
      return output.toByteArray();
    }
  }

  @Test
  void nativeExtractionStillBoundsCompressedFontResources() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      page.setResources(new PDResources());
      var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      var unicode = new PDStream(document,
          new ByteArrayInputStream("% oversized synthetic unicode map\n".repeat(100)
              .getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE);
      font.getCOSObject().setItem(COSName.TO_UNICODE, unicode);
      page.getResources().put(COSName.getPDFName("F1"), font);
      page.setContents(new PDStream(document,
          new ByteArrayInputStream("BT /F1 1 Tf 1 90 Td (Resume skills) Tj ET\n"
              .getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE));
      document.addPage(page);
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 128L, 4_096));
    assertThatThrownBy(() -> adapter.extract("expanded-font.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void nativeExtractionStillBoundsCompressedFormContent() throws Exception {
    byte[] bytes;
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      page.setResources(new PDResources());
      var stream = new PDStream(document,
          new ByteArrayInputStream("% oversized synthetic form content\n".repeat(100)
              .getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE);
      var form = new PDFormXObject(stream);
      form.setResources(new PDResources());
      form.setBBox(new PDRectangle(100, 100));
      page.getResources().put(COSName.getPDFName("Fm1"), form);
      page.setContents(new PDStream(document,
          new ByteArrayInputStream("/Fm1 Do\n".getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE));
      document.addPage(page);
      document.save(output);
      bytes = output.toByteArray();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500_000, 128L, 4_096));
    assertThatThrownBy(() -> adapter.extract("expanded-form.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void nativeExtractionStillBoundsContentOperatorsWithoutRequiringImageRecognition() throws Exception {
    byte[] bytes = pdfWithNativeRawContent(nativeResumeContent() + "q Q\n".repeat(3_000));
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal, properties(500, 67_108_864L, 128));
    assertThatThrownBy(() -> adapter.extract("native-operator-limit.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  private static String nativeResumeContent() {
    StringBuilder content = new StringBuilder();
    for (int index = 0; index < NATIVE_RESUME_LINES.length; index++) {
      content.append("BT /F1 1 Tf 1 ").append(90 - index * 4).append(" Td (")
          .append(NATIVE_RESUME_LINES[index]).append(") Tj ET\n");
    }
    return content.toString();
  }

  private static byte[] pdfWithNativeRawContent(String content) throws Exception {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      page.setResources(new PDResources());
      page.getResources().put(COSName.getPDFName("F1"),
          new PDType1Font(Standard14Fonts.FontName.HELVETICA));
      page.setContents(new PDStream(document,
          new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE));
      document.addPage(page);
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithLargeSourceImage(boolean nativeText) throws Exception {
    var image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(LosslessFactory.createFromImage(document, image), 0, 0, 100, 100);
        if (nativeText) {
          drawNativeResume(content);
        }
      }
      document.save(output);
      return output.toByteArray();
    } finally {
      image.flush();
    }
  }

  @Test
  void rejectsOversizedSoftMaskAndNestedFormImageBeforeAnyFallback() throws Exception {
    for (boolean nestedForm : List.of(false, true)) {
      byte[] bytes;
      var large = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
      var small = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
      try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
        var page = new PDPage(new PDRectangle(100, 100));
        document.addPage(page);
        var image = LosslessFactory.createFromImage(document, nestedForm ? large : small);
        if (!nestedForm) {
          image.getCOSObject().setItem(COSName.SMASK, LosslessFactory.createFromImage(document, large));
        }
        try (var content = new PDPageContentStream(document, page)) {
          if (nestedForm) {
            var form = new PDFormXObject(document);
            form.setResources(new PDResources());
            form.setBBox(new PDRectangle(100, 100));
            try (var formContent = new PDFormContentStream(form)) {
              formContent.drawImage(image, 0, 0, 100, 100);
            }
            content.drawForm(form);
          } else {
            content.drawImage(image, 0, 0, 100, 100);
          }
        }
        document.save(output);
        bytes = output.toByteArray();
      } finally {
        large.flush();
        small.flush();
      }
      var ocr = mock(ResumeOcrPort.class);
      var multimodal = mock(ResumeMultimodalPagePort.class);
      var configuration = properties();
      configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
      var adapter = adapter(ocr, multimodal, configuration);
      assertThatThrownBy(() -> adapter.extract("nested-image.pdf", new ByteArrayInputStream(bytes)))
          .isInstanceOf(IOException.class).hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:1");
      verifyNoInteractions(ocr, multimodal);
    }
  }

  @Test
  void rejectsInlineSourceImageBeforePdfBoxCreatesTheRaster() throws Exception {
    byte[] bytes = pdfWithRawContent("BI /W 400 /H 400 /BPC 8 /CS /RGB ID abc EI\n", null);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var adapter = adapter(ocr, multimodal, configuration);
    assertThatThrownBy(() -> adapter.extract("inline.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:1");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsSourceImageAboveDefaultPixelOrDimensionLimitBeforeDecode() throws Exception {
    for (String dimensions : List.of("/W 8000 /H 4001", "/W 20001 /H 1", "/W 0 /H 10")) {
      byte[] bytes = pdfWithRawContent("BI " + dimensions + " /BPC 8 /CS /RGB ID abc EI\n", null);
      var ocr = mock(ResumeOcrPort.class);
      var multimodal = mock(ResumeMultimodalPagePort.class);
      var adapter = adapter(ocr, multimodal);
      assertThatThrownBy(() -> adapter.extract("invalid-dimensions.pdf", new ByteArrayInputStream(bytes)))
          .isInstanceOf(IOException.class).hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:1");
      verifyNoInteractions(ocr, multimodal);
    }
  }

  @Test
  void rejectsPredictorRowBeforeTheFilterAllocatesIt() throws Exception {
    var parameters = new COSDictionary();
    parameters.setInt(COSName.PREDICTOR, 12);
    parameters.setInt(COSName.COLUMNS, 20_001);
    byte[] bytes = pdfWithRawContent("% small synthetic payload\n", parameters);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal);
    assertThatThrownBy(() -> adapter.extract("predictor.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsGraphicsStateAccumulationBeforePdfBoxCopiesAnotherState() throws Exception {
    byte[] bytes = pdfWithRawContent("q\n".repeat(129), null);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal);
    assertThatThrownBy(() -> adapter.extract("state-stack.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_LIMIT");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsImageMaskCycleWithoutRecursiveDecode() throws Exception {
    byte[] bytes;
    var source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      document.addPage(page);
      var image = LosslessFactory.createFromImage(document, source);
      image.getCOSObject().setItem(COSName.SMASK, image);
      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(image, 0, 0, 100, 100);
      }
      document.save(output);
      bytes = output.toByteArray();
    } finally {
      source.flush();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var adapter = adapter(ocr, multimodal);
    assertThatThrownBy(() -> adapter.extract("mask-cycle.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_CONTAINER_INVALID");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsJpegHeaderDimensionsThatDisagreeWithPdfDictionary() throws Exception {
    byte[] bytes;
    var source = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      document.addPage(page);
      var image = JPEGFactory.createFromImage(document, source);
      image.setWidth(10);
      image.setHeight(10);
      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(image, 0, 0, 100, 100);
      }
      document.save(output);
      bytes = output.toByteArray();
    } finally {
      source.flush();
    }
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    var configuration = properties();
    configuration.getDocumentExtraction().setMaximumSourceImagePixels(100_000L);
    var adapter = adapter(ocr, multimodal, configuration);
    assertThatThrownBy(() -> adapter.extract("false-dimensions.pdf", new ByteArrayInputStream(bytes)))
        .isInstanceOf(IOException.class).hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:1");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void reusesSafeJpegImageWithinTheDocumentBudget() throws Exception {
    byte[] bytes;
    var source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      document.addPage(page);
      var image = JPEGFactory.createFromImage(document, source);
      try (var content = new PDPageContentStream(document, page)) {
        for (int repeat = 0; repeat < 20; repeat++) {
          content.drawImage(image, 0, 0, 100, 100);
        }
      }
      document.save(output);
      bytes = output.toByteArray();
    } finally {
      source.flush();
    }
    String recognized = "教育经历：计算机本科；项目经历：服务开发；技能：Java；手机：13800000000。";
    var adapter = adapter((page, image) -> Optional.of(recognized), (page, image) -> Optional.empty(),
        properties(500_000, 100_000L, 4_096));
    var extracted = adapter.extract("safe-jpeg.pdf", new ByteArrayInputStream(bytes));
    assertThat(extracted.pages().getFirst().text()).isEqualTo(recognized);
  }

  private static byte[] pdfWithRawContent(String content, COSDictionary parameters) throws Exception {
    try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
      var page = new PDPage(new PDRectangle(100, 100));
      document.addPage(page);
      var stream = new PDStream(document,
          new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE);
      if (parameters != null) {
        stream.getCOSObject().setItem(COSName.DECODE_PARMS, parameters);
      }
      page.setContents(stream);
      document.save(output);
      return output.toByteArray();
    }
  }

  @Test
  void rejectsPdfAboveConfiguredPageLimitBeforeRenderingOrOcr() throws Exception {
    byte[] document = pdfWithPages(101);
    var ocr = mock(ResumeOcrPort.class);
    var multimodal =
        mock(ResumeMultimodalPagePort.class);
    LayeredResumeDocumentExtractionAdapter adapter = adapter(ocr, multimodal);

    assertThatThrownBy(() -> adapter.extract("oversized.pdf", new ByteArrayInputStream(document)))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_PAGE_LIMIT");

    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void downscalesLargeResumePageWithinPixelBudgetBeforeCallingFallback() throws Exception {
    byte[] document = pdfWithPage(new PDRectangle(1_664, 2_224));
    byte[][] rendered = new byte[1][];
    String recognized = "教育经历：计算机本科；项目经历：视频制作；技能：Premiere、After Effects；手机：13800000000。";
    ResumeOcrPort ocr = (page, image) -> {
      rendered[0] = image;
      return Optional.of(recognized);
    };
    var multimodal = mock(ResumeMultimodalPagePort.class);
    LayeredResumeDocumentExtractionAdapter adapter = adapter(ocr, multimodal);

    ExtractedDocument result =
        adapter.extract("large-resume-page.pdf", new ByteArrayInputStream(document));
    var image = ImageIO.read(new ByteArrayInputStream(rendered[0]));

    assertThat(result.pages().getFirst().text()).isEqualTo(recognized);
    assertThat((long) image.getWidth() * image.getHeight()).isLessThanOrEqualTo(8_000_000L);
    assertThat(image.getWidth()).isLessThan((int) Math.ceil(1_664 * 150 / 72.0d));
    verifyNoInteractions(multimodal);
  }

  @Test
  void treatsNearWhiteTrailingPageAsBlankWithoutCallingFallbacks() throws Exception {
    var ocr = mock(ResumeOcrPort.class);
    var multimodal = mock(ResumeMultimodalPagePort.class);
    LayeredResumeDocumentExtractionAdapter adapter = adapter(ocr, multimodal);

    ExtractedDocument result = adapter.extract(
        "near-white-tail.pdf", new ByteArrayInputStream(pdfWithNearWhiteTrailingPage()));

    assertThat(result.pages()).hasSize(2);
    assertThat(result.pages().get(1).route()).isEqualTo("BLANK_PAGE");
    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsExtremePdfPageDimensionEvenWhenTotalPixelCountIsSmall() throws Exception {
    byte[] document = pdfWithPage(new PDRectangle(5_000, 1));
    var ocr = mock(ResumeOcrPort.class);
    var multimodal =
        mock(ResumeMultimodalPagePort.class);
    LayeredResumeDocumentExtractionAdapter adapter = adapter(ocr, multimodal);

    assertThatThrownBy(() -> adapter.extract("extreme-page.pdf", new ByteArrayInputStream(document)))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_PAGE_RENDER_LIMIT:1");

    verifyNoInteractions(ocr, multimodal);
  }

  @Test
  void rejectsCompressedDocxThatExpandsBeyondTheExtractedTextLimit() throws Exception {
    byte[] document;
    try (XWPFDocument value = new XWPFDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      value.createParagraph().createRun().setText(OVERSIZED_EXTRACTED_TEXT);
      value.write(output);
      document = output.toByteArray();
    }
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter((page, image) -> Optional.empty(), (page, image) -> Optional.empty());

    assertThatThrownBy(() -> adapter.extract("oversized-text.docx", new ByteArrayInputStream(document)))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_TEXT_LIMIT");
  }

  @Test
  void rejectsOversizedOcrOutputBeforeCleaningOrPersistingIt() throws Exception {
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter(
            (page, image) -> Optional.of(OVERSIZED_EXTRACTED_TEXT),
            (page, image) -> {
              throw new AssertionError("multimodal must not run after an oversized OCR response");
            });

    assertThatThrownBy(() -> adapter.extract("scan.pdf", new ByteArrayInputStream(blankPdf())))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_TEXT_LIMIT:1");
  }

  @Test
  void normalizesHighlyCompressedDocxAsStableContainerLimitFailure() throws Exception {
    byte[] document;
    try (XWPFDocument value = new XWPFDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      value.createParagraph().createRun().setText(HIGHLY_COMPRESSED_TEXT);
      value.write(output);
      document = output.toByteArray();
    }
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter((page, image) -> Optional.empty(), (page, image) -> Optional.empty());

    assertThatThrownBy(
            () -> adapter.extract("compressed.docx", new ByteArrayInputStream(document)))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_CONTAINER_LIMIT");
  }

  @Test
  void rejectsDocxBeforePoiWhenExpandedContainerExceedsConfiguredLimit() throws Exception {
    byte[] document = docx(randomizedText(1_100_000));
    IntegrationProperties properties = properties(2_000_000, 1_048_576L, 4_096);
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter(
            (page, image) -> Optional.empty(),
            (page, image) -> Optional.empty(),
            properties);

    assertThatThrownBy(
            () -> adapter.extract("expanded.docx", new ByteArrayInputStream(document)))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_CONTAINER_LIMIT");
  }

  @Test
  void rejectsDocxContainerEntryFloodBeforePoiObjectModelCreation() throws Exception {
    byte[] document = zipWithEntries(3);
    IntegrationProperties properties =
        properties(CONFIGURED_MAXIMUM_EXTRACTED_CHARACTERS, 67_108_864L, 2);
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter(
            (page, image) -> Optional.empty(),
            (page, image) -> Optional.empty(),
            properties);

    assertThatThrownBy(() -> adapter.extract("entry-flood.docx", new ByteArrayInputStream(document)))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_CONTAINER_LIMIT");
  }

  @Test
  void normalizesMalformedDocxAsStableContainerInvalidFailure() {
    LayeredResumeDocumentExtractionAdapter adapter =
        adapter((page, image) -> Optional.empty(), (page, image) -> Optional.empty());

    assertThatThrownBy(
            () ->
                adapter.extract(
                    "malformed.docx",
                    new ByteArrayInputStream("not-a-zip".getBytes(StandardCharsets.US_ASCII))))
        .isInstanceOf(IOException.class)
        .hasMessage("DOCUMENT_CONTAINER_INVALID");
  }

  private static IntegrationProperties properties() {
    var properties = new IntegrationProperties();
    properties.getDocumentExtraction().setMinimumPageCharacters(20);
    return properties;
  }

  private static IntegrationProperties properties(
      int maximumExtractedCharacters,
      long maximumContainerExpandedBytes,
      int maximumContainerEntries) {
    var properties = properties();
    var extraction = properties.getDocumentExtraction();
    extraction.setMaximumExtractedCharacters(maximumExtractedCharacters);
    extraction.setMaximumContainerExpandedBytes(maximumContainerExpandedBytes);
    extraction.setMaximumPdfExpandedBytes(maximumContainerExpandedBytes);
    extraction.setMaximumContainerEntries(maximumContainerEntries);
    return properties;
  }

  /** 使用固定种子构造不可高度压缩的边界样本，避免测试被 POI ZIP 炸弹门禁提前截断。 */
  private static String oversizedExtractedText() {
    return randomizedText(CONFIGURED_MAXIMUM_EXTRACTED_CHARACTERS + 1);
  }

  private static String randomizedText(int length) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    Random random = new Random(20260905L);
    StringBuilder text = new StringBuilder(length);
    text.append("教育经历项目经历技能");
    while (text.length() < length) {
      text.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return text.toString();
  }

  private static LayeredResumeDocumentExtractionAdapter adapter(
      ResumeOcrPort ocr,
      ResumeMultimodalPagePort multimodal) {
    return adapter(ocr, multimodal, properties());
  }

  private static LayeredResumeDocumentExtractionAdapter adapter(
      ResumeOcrPort ocr,
      ResumeMultimodalPagePort multimodal,
      IntegrationProperties properties) {
    LayeredResumeDocumentExtractionAdapter adapter = new LayeredResumeDocumentExtractionAdapter();
    ReflectionTestUtils.setField(adapter, "ocr", ocr);
    ReflectionTestUtils.setField(adapter, "multimodal", multimodal);
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ReflectionTestUtils.invokeMethod(adapter, "initializeQualityThresholds");
    return adapter;
  }

  private static byte[] docx(String text) throws Exception {
    try (XWPFDocument document = new XWPFDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.createParagraph().createRun().setText(text);
      document.write(output);
      return output.toByteArray();
    }
  }

  private static byte[] zipWithEntries(int entryCount) throws Exception {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream archive = new ZipOutputStream(output)) {
      for (int index = 0; index < entryCount; index++) {
        archive.putNextEntry(new ZipEntry("entry-" + index));
        archive.write(index);
        archive.closeEntry();
      }
      archive.finish();
      return output.toByteArray();
    }
  }

  private static byte[] blankPdf() throws Exception {
    return pdfWithPages(1);
  }

  private static byte[] pdfWithNearWhiteTrailingPage() throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage contentPage = new PDPage();
      document.addPage(contentPage);
      try (PDPageContentStream content = new PDPageContentStream(document, contentPage)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        drawLine(content, 40, 700,
            "Resume education experience projects skills phone 13800000000 and server operations");
      }
      PDPage nearWhitePage = new PDPage();
      document.addPage(nearWhitePage);
      try (PDPageContentStream content = new PDPageContentStream(document, nearWhitePage)) {
        float nearWhite = 254 / 255.0f;
        content.setNonStrokingColor(nearWhite, nearWhite, nearWhite);
        content.addRect(40, 700, 100, 20);
        content.fill();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithPages(int pageCount) throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      for (int page = 0; page < pageCount; page++) {
        document.addPage(new PDPage());
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithPage(PDRectangle pageSize) throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage(pageSize));
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithReadableTextAndUnmappedCid() {
    return pdfWithReadableTextAndUnmappedCid(false);
  }

  private static byte[] pdfWithReadableTextAndUnmappedCid(boolean transparentDiagonalWatermark) {
    return pdfWithReadableTextAndUnmappedCid(transparentDiagonalWatermark, false);
  }

  private static byte[] pdfWithReadableTextAndUnmappedCid(
      boolean transparentDiagonalWatermark, boolean healthySecondPage) {
    List<String> objects = List.of(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R" + (healthySecondPage ? " 9 0 R" : "")
            + "] /Count " + (healthySecondPage ? 2 : 1) + " >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            + "/Resources << /Font << /F0 4 0 R /F1 5 0 R >> "
            + "/ExtGState << /GS1 8 0 R >> >> /Contents 7 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        "<< /Type /Font /Subtype /Type0 /BaseFont /SyntheticMissingUnicode "
            + "/Encoding /Identity-H /DescendantFonts [6 0 R] >>",
        "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /SyntheticMissingUnicode "
            + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> "
            + "/DW 1000 /CIDToGIDMap /Identity >>",
        "STREAM",
        "<< /Type /ExtGState /ca 0.00784 /CA 0.00784 >>",
        healthySecondPage
            ? "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << /Font << /F0 4 0 R >> >> /Contents 10 0 R >>"
            : "<<>>",
        healthySecondPage ? "SECOND_STREAM" : "<<>>");
    String stream = "BT /F0 12 Tf 40 700 Td "
        + "(Education Experience Project Skills Java phone 13800000000) Tj "
        + (transparentDiagonalWatermark
            ? "/GS1 gs /F1 12 Tf 0.766 0.643 -0.643 0.766 300 300 Tm <7777> Tj ET"
            : "/F1 12 Tf <7777> Tj ET");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writeAscii(output, "%PDF-1.7\n");
    List<Integer> offsets = new ArrayList<>();
    for (int index = 0; index < objects.size(); index++) {
      offsets.add(output.size());
      writeAscii(output, (index + 1) + " 0 obj\n");
      if ("STREAM".equals(objects.get(index)) || "SECOND_STREAM".equals(objects.get(index))) {
        String pageStream = "SECOND_STREAM".equals(objects.get(index))
            ? "BT /F0 12 Tf 40 700 Td (Education Experience Project Skills Java phone 13900000000) Tj ET"
            : stream;
        writeAscii(output, "<< /Length " + pageStream.length() + " >>\nstream\n" + pageStream
            + "\nendstream\n");
      } else {
        writeAscii(output, objects.get(index) + "\n");
      }
      writeAscii(output, "endobj\n");
    }
    int xref = output.size();
    writeAscii(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
    for (int offset : offsets) writeAscii(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
    writeAscii(output, "trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
        + xref + "\n%%EOF\n");
    return output.toByteArray();
  }

  private static void writeAscii(ByteArrayOutputStream output, String value) {
    output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
  }
}
