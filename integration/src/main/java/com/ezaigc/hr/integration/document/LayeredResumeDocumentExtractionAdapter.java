package com.ezaigc.hr.integration.document;

import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;

import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeMultimodalPagePort;
import com.ezaigc.hr.talent.application.provider.ResumeOcrPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class LayeredResumeDocumentExtractionAdapter implements ResumeDocumentExtractionPort {

    /**
     * 技术乐观锁版本号。
     */
    private static final String VERSION = "native-pdfbox-3.0.8-poi-5.5.1-v23";
    private static final String BLANK_PAGE = "BLANK_PAGE";
    private static final String NATIVE_TEXT_VISUAL_LIMIT = "NATIVE_TEXT_VISUAL_LIMIT";
    private static final String DOCUMENT_TEXT_LIMIT = "DOCUMENT_TEXT_LIMIT";
    private static final String DOCUMENT_CONTAINER_LIMIT = "DOCUMENT_CONTAINER_LIMIT";
    private static final String DOCUMENT_CONTAINER_INVALID = "DOCUMENT_CONTAINER_INVALID";
    private static final int CONTAINER_SCAN_BUFFER_SIZE = 8_192;
    private static final int MINIMUM_ADAPTIVE_RENDER_DPI = 72;
    private static final int MAXIMUM_PDF_GRAPHICS_DEPTH = 128;
    private static final int PDF_CONTENT_TOKEN_OVERHEAD = 4_096;
    private static final int BLANK_PAGE_MINIMUM_CHANNEL = 250;
    private static final Set<COSName> TEXT_DEPENDENCIES = Set.of(
            COSName.CONTENTS, COSName.RESOURCES, COSName.XOBJECT, COSName.FONT, COSName.FONT_DESC,
            COSName.FONT_FILE, COSName.FONT_FILE2, COSName.FONT_FILE3, COSName.TO_UNICODE,
            COSName.ENCODING, COSName.CID_TO_GID_MAP, COSName.DESCENDANT_FONTS,
            COSName.CHAR_PROCS, COSName.EXT_G_STATE, COSName.PATTERN);
    private static final Set<COSName> TEXT_RESOURCE_MAPS = Set.of(
            COSName.FONT, COSName.CHAR_PROCS, COSName.EXT_G_STATE, COSName.PATTERN);
    private static final Pattern CONTINUATION_WORDS = Pattern.compile("(?i)\\b[a-z]{3,}(?: +[a-z]{3,}){2,}\\b");
    @Resource
    private ResumeOcrPort ocr;
    @Resource
    private ResumeMultimodalPagePort multimodal;
    @Resource
    private IntegrationProperties properties;
    private int maximumPages;
    private int maximumExtractedCharacters;
    private long maximumContainerExpandedBytes;
    private int maximumContainerEntries;
    private int maximumPdfResourceNodes;
    private long maximumPdfExpandedBytes;
    private long maximumSourceImagePixels;
    private int maximumSourceImageDimension;
    private int renderDpi;
    private long maximumRenderedPagePixels;
    private int maximumRenderedPageDimension;
    private int minimumCharacters;
    private double minimumPrintableRatio;

    @PostConstruct
    void initializeQualityThresholds() {
        maximumPages = properties.getDocumentExtraction().getMaximumPages();
        maximumExtractedCharacters =
                properties.getDocumentExtraction().getMaximumExtractedCharacters();
        maximumContainerExpandedBytes =
                properties.getDocumentExtraction().getMaximumContainerExpandedBytes();
        maximumContainerEntries =
                properties.getDocumentExtraction().getMaximumContainerEntries();
        maximumPdfResourceNodes =
                properties.getDocumentExtraction().getMaximumPdfResourceNodes();
        maximumPdfExpandedBytes = properties.getDocumentExtraction().getMaximumPdfExpandedBytes();
        maximumSourceImagePixels = properties.getDocumentExtraction().getMaximumSourceImagePixels();
        maximumSourceImageDimension = properties.getDocumentExtraction().getMaximumSourceImageDimension();
        renderDpi = properties.getDocumentExtraction().getRenderDpi();
        maximumRenderedPagePixels =
                properties.getDocumentExtraction().getMaximumRenderedPagePixels();
        maximumRenderedPageDimension =
                properties.getDocumentExtraction().getMaximumRenderedPageDimension();
        minimumCharacters = properties.getDocumentExtraction().getMinimumPageCharacters();
        minimumPrintableRatio = properties.getDocumentExtraction().getMinimumPrintableRatio();
    }

    @Override
    public ExtractedDocument extract(String fileName, InputStream content) throws IOException {
        byte[] bytes = content.readNBytes(Math.toIntExact(MAX_FILE_SIZE_BYTES + 1));
      if (bytes.length > MAX_FILE_SIZE_BYTES) {
        throw new IOException("document exceeds extraction limit");
      }
        String lower = fileName.toLowerCase(Locale.ROOT);
      if (lower.endsWith(".pdf")) {
        return extractPdf(bytes);
      }
      if (lower.endsWith(".docx")) {
        return singlePage(extractDocx(bytes));
      }
      if (lower.endsWith(".doc")) {
        return singlePage(extractDoc(bytes));
      }
        throw new IOException("unsupported resume document type");
    }

    private ExtractedDocument extractPdf(byte[] bytes) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        int fallbacks = 0;
        int extractedCharacters = 0;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > maximumPages) {
                throw new IOException("DOCUMENT_PAGE_LIMIT");
            }
            // 文字依赖流先安全解压；源图像只在实际需要渲染识别时进入预算检查。
            PdfResourceBudget resourceBudget = new PdfResourceBudget();
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                resourceBudget.validatePage(document.getPage(index), index + 1, false);
            }
            long maximumOperators = (long) maximumExtractedCharacters + PDF_CONTENT_TOKEN_OVERHEAD;
            int maximumGraphicsDepth = MAXIMUM_PDF_GRAPHICS_DEPTH;
            ResumePdfTextStripper stripper = new ResumePdfTextStripper(
                    maximumExtractedCharacters, maximumOperators, maximumGraphicsDepth);
            // 绘制流可能先写完整日期列；按可见位置恢复同一行的雇主与任职日期。
            stripper.setSortByPosition(true);
            PDFRenderer renderer = new BudgetedPdfRenderer(document, maximumOperators, maximumGraphicsDepth);
            // 渲染前仍需约束源数组；降低目标采样成本不能代替源图像门禁。
            renderer.setSubsamplingAllowed(true);
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                int pageNo = index + 1;
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                stripper.beginPage(pageNo);
                int remainingCharacters = maximumExtractedCharacters - extractedCharacters;
                String text =
                        extractNativePdfPage(
                                stripper, document, remainingCharacters, pageNo);
                String route = "NATIVE_TEXT";
                boolean continuation = !pages.isEmpty();
                boolean nativeTextAccepted = !stripper.hasUnmappedVisibleGlyphs()
                        && pageQuality(text, continuation);
                if (!nativeTextAccepted) {
                    resourceBudget.validatePage(document.getPage(index), pageNo, true);
                    byte[] image = render(renderer, document.getPage(index), index, pageNo);
                    if (continuation && text.isBlank() && isVisuallyBlank(image)) {
                        pages.add(new ExtractedPage(pageNo, BLANK_PAGE, "", 0, 1, 1, hash("")));
                        continue;
                    }
                    Optional<String> ocrText =
                            cleanPageCandidate(
                                    ocr.recognize(pageNo, image), remainingCharacters, pageNo);
                    if (ocrText.filter(value -> pageQuality(value, continuation)).isPresent()) {
                        text = ocrText.get();
                        route = "OCR_TEXT";
                        fallbacks++;
                    } else {
                        Optional<String> vision =
                                cleanPageCandidate(
                                        multimodal.extractText(pageNo, image),
                                        remainingCharacters,
                                        pageNo);
                      if (vision.filter(value -> pageQuality(value, continuation)).isEmpty()) {
                        if (continuation && isDecoratedBlankPage(image)) {
                          pages.add(new ExtractedPage(pageNo, BLANK_PAGE, "", 0, 1, 1, hash("")));
                          continue;
                        }
                        throw new IOException("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:" + pageNo);
                      }
                        text = vision.get();
                        route = "MULTIMODAL";
                        fallbacks++;
                    }
                }
                // 首页的大幅图片或碎片图片可能含身份信息，而原生文字已足够通过质量门禁。
                if (pageNo == 1 && nativeTextAccepted && stripper.hasVisualTextLayer()) {
                    try {
                        resourceBudget.validatePage(document.getPage(index), pageNo, true);
                        byte[] image = render(renderer, document.getPage(index), index, pageNo);
                        Optional<String> visual = cleanPageCandidate(
                                ocr.recognize(pageNo, image), remainingCharacters, pageNo)
                                .filter(value -> pageQuality(value, false));
                        String visualRoute = "OCR_TEXT";
                        if (visual.isEmpty()) {
                            visual = cleanPageCandidate(
                                    multimodal.extractText(pageNo, image), remainingCharacters, pageNo)
                                    .filter(value -> pageQuality(value, false));
                            visualRoute = "MULTIMODAL";
                        }
                        if (visual.isPresent() && !text.contains(visual.get())) {
                            String supplemented = text + "\n" + visual.get();
                            if (supplemented.length() <= remainingCharacters) {
                                text = supplemented;
                                route = visualRoute;
                                fallbacks++;
                            }
                        }
                    } catch (IOException error) {
                        if (isPdfBudgetFailure(error)) {
                            route = NATIVE_TEXT_VISUAL_LIMIT;
                        }
                        // 可用的原生文字不因补充识别失败而回退为整页失败。
                    } catch (RuntimeException ignored) {
                        // 可用的原生文字不因补充识别失败而回退为整页失败。
                    }
                }
                if (text.length() > maximumExtractedCharacters - extractedCharacters) {
                    throw textLimit(pageNo);
                }
                extractedCharacters += text.length();
                pages.add(page(pageNo, route, text));
            }
        }
      if (pages.isEmpty()) {
        throw new IOException("DOCUMENT_EMPTY");
      }
        String route = aggregateRoute(pages);
        double printable =
                pages.stream().mapToDouble(ExtractedPage::printableRatio).average().orElse(0);
        double coverage = (double) pages.stream().filter(v -> BLANK_PAGE.equals(v.route()) || pageQuality(v.text(), v.pageNo() > 1)).count() / pages.size();
        return new ExtractedDocument(
                VERSION,
                route,
                pages.size(),
                (int) pages.stream().filter(v -> BLANK_PAGE.equals(v.route()) || pageQuality(v.text(), v.pageNo() > 1)).count(),
                fallbacks,
                printable,
                coverage,
                pages);
    }

    private ExtractedDocument singlePage(String text) throws IOException {
        String cleaned = cleanWithinLimit(text, maximumExtractedCharacters, null);
      if (!quality(cleaned)) {
        throw new IOException("DOCUMENT_TEXT_QUALITY_LOW");
      }
        ExtractedPage page = page(1, "NATIVE_TEXT", cleaned);
        return new ExtractedDocument(
                VERSION, "NATIVE_TEXT", 1, 1, 0, page.printableRatio(), 1, List.of(page));
    }

    private String extractDocx(byte[] bytes) throws IOException {
        validateDocxContainer(bytes);
        try (XWPFDocument document = openDocx(bytes)) {
            StringBuilder text = new StringBuilder();
            for (var paragraph : document.getParagraphs()) {
                appendWithinLimit(text, paragraph.getText(), '\n');
            }
            for (var table : document.getTables()) {
                for (var row : table.getRows()) {
                    for (var cell : row.getTableCells()) {
                        appendWithinLimit(text, cell.getText(), '\t');
                    }
                }
            }
            return text.toString();
        }
    }

    /**
     * 在 POI 构建 OOXML 对象模型前流式扫描容器。扫描不落盘、不保留条目内容，并沿用 POI 的压缩比安全阈值。
     */
    private void validateDocxContainer(byte[] bytes) throws IOException {
        byte[] buffer = new byte[CONTAINER_SCAN_BUFFER_SIZE];
        int entryCount = 0;
        long totalExpandedBytes = 0;
        try (ZipInputStream archive =
                new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++entryCount > maximumContainerEntries) {
                    throw new DocumentContainerLimitException();
                }
                long entryExpandedBytes = 0;
                int read;
                while ((read = archive.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    if (read > maximumContainerExpandedBytes - totalExpandedBytes) {
                        throw new DocumentContainerLimitException();
                    }
                    entryExpandedBytes += read;
                    totalExpandedBytes += read;
                }
                requireSafeCompressionRatio(entry, entryExpandedBytes);
                archive.closeEntry();
            }
        } catch (DocumentContainerLimitException exception) {
            throw exception;
        } catch (IOException exception) {
            throw containerInvalid(exception);
        }
        if (entryCount == 0) {
            throw containerInvalid(null);
        }
    }

    private static void requireSafeCompressionRatio(ZipEntry entry, long expandedBytes)
            throws IOException {
        long compressedBytes = entry.getCompressedSize();
        if (expandedBytes > ZipSecureFile.getGraceEntrySize()
                && compressedBytes >= 0
                && (double) compressedBytes / expandedBytes
                        < ZipSecureFile.getMinInflateRatio()) {
            throw new DocumentContainerLimitException();
        }
    }

    /** 将 POI/ZIP 实现细节保留为 cause，仅向任务状态暴露稳定的平台错误码。 */
    private static XWPFDocument openDocx(byte[] bytes) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return new XWPFDocument(input);
        } catch (IOException | RuntimeException exception) {
            if (isContainerLimit(exception)) {
                throw new DocumentContainerLimitException(exception);
            }
            throw containerInvalid(exception);
        }
    }

    private static boolean isContainerLimit(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                    && (message.contains("Zip bomb detected")
                            || message.contains("maximum file count"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static IOException containerInvalid(Throwable cause) {
        return new IOException(DOCUMENT_CONTAINER_INVALID, cause);
    }

    private String extractDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
                WordExtractor extractor = new WordExtractor(document)) {
            return requireTextWithinLimit(extractor.getText(), maximumExtractedCharacters, null);
        }
    }

    /** 直接把 PDFBox 输出写入有界 Writer，避免先构造无界字符串再做事后校验。 */
    private static String extractNativePdfPage(
            PDFTextStripper stripper,
            PDDocument document,
            int maximumCharacters,
            int pageNo)
            throws IOException {
        BoundedTextWriter output =
                new BoundedTextWriter(maximumCharacters, textLimitMessage(pageNo));
        stripper.writeText(document, output);
        return clean(output.value());
    }

    /** 外部 OCR/多模态响应必须在正则清洗、持久化和模型消费前执行长度门禁。 */
    private static Optional<String> cleanPageCandidate(
            Optional<String> candidate, int maximumCharacters, int pageNo) throws IOException {
        if (candidate == null || candidate.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cleanWithinLimit(candidate.get(), maximumCharacters, pageNo));
    }

    private void appendWithinLimit(StringBuilder output, String value, char separator)
            throws IOException {
        long resultingLength = (long) output.length() + value.length() + 1;
        if (resultingLength > maximumExtractedCharacters) {
            throw textLimit(null);
        }
        output.append(value).append(separator);
    }

    private static String cleanWithinLimit(String text, int maximumCharacters, Integer pageNo)
            throws IOException {
        return clean(requireTextWithinLimit(text, maximumCharacters, pageNo));
    }

    private static String requireTextWithinLimit(
            String text, int maximumCharacters, Integer pageNo) throws IOException {
        if (text.length() > maximumCharacters) {
            throw textLimit(pageNo);
        }
        return text;
    }

    private byte[] render(PDFRenderer renderer, PDPage page, int pageIndex, int pageNo)
            throws IOException {
        int effectiveDpi = safeRenderDpi(page, pageNo);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, effectiveDpi, ImageType.RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("DOCUMENT_PAGE_RENDER_ENCODING_UNAVAILABLE:" + pageNo);
            }
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    /** 在 PDFBox 分配 BufferedImage 前选择安全 DPI，避免大画布耗尽堆内存，同时允许常见超长简历降采样。 */
    private int safeRenderDpi(PDPage page, int pageNo) throws IOException {
        PDRectangle cropBox = page == null ? null : page.getCropBox();
        if (cropBox == null) {
            throw renderLimit(pageNo);
        }
        double widthPoints = cropBox.getWidth();
        double heightPoints = cropBox.getHeight();
        if (!Double.isFinite(widthPoints)
                || !Double.isFinite(heightPoints)
                || widthPoints <= 0
                || heightPoints <= 0) {
            throw renderLimit(pageNo);
        }

        long targetWidth = renderedPixels(widthPoints, renderDpi, pageNo);
        long targetHeight = renderedPixels(heightPoints, renderDpi, pageNo);
        if (targetWidth > maximumRenderedPageDimension
                || targetHeight > maximumRenderedPageDimension) {
            throw renderLimit(pageNo);
        }
        if (withinPixelBudget(targetWidth, targetHeight)) {
            return renderDpi;
        }

        int minimumDpi = Math.min(renderDpi, MINIMUM_ADAPTIVE_RENDER_DPI);
        double pixelsAtOneDpi = widthPoints * heightPoints / (72.0d * 72.0d);
        int effectiveDpi = (int) Math.floor(Math.sqrt(maximumRenderedPagePixels / pixelsAtOneDpi));
        effectiveDpi = Math.min(renderDpi, effectiveDpi);
        while (effectiveDpi >= minimumDpi) {
            long width = renderedPixels(widthPoints, effectiveDpi, pageNo);
            long height = renderedPixels(heightPoints, effectiveDpi, pageNo);
            if (width <= maximumRenderedPageDimension
                    && height <= maximumRenderedPageDimension
                    && withinPixelBudget(width, height)) {
                return effectiveDpi;
            }
            effectiveDpi--;
        }
        throw renderLimit(pageNo);
    }

    private static long renderedPixels(double points, int dpi, int pageNo) throws IOException {
        double pixels = Math.ceil(points * dpi / 72.0d);
        if (!Double.isFinite(pixels) || pixels < 1 || pixels > Long.MAX_VALUE) {
            throw renderLimit(pageNo);
        }
        return (long) pixels;
    }

    private boolean withinPixelBudget(long width, long height) {
        return height > 0 && width <= maximumRenderedPagePixels / height;
    }

    private static IOException renderLimit(int pageNo) {
        return new IOException("DOCUMENT_PAGE_RENDER_LIMIT:" + pageNo);
    }

    private static IOException textLimit(Integer pageNo) {
        return new IOException(textLimitMessage(pageNo));
    }

    private static String textLimitMessage(Integer pageNo) {
        return pageNo == null ? DOCUMENT_TEXT_LIMIT : DOCUMENT_TEXT_LIMIT + ":" + pageNo;
    }

    private ExtractedPage page(int pageNo, String route, String text) {
        double printable = printableRatio(text);
        double anchors = anchorScore(text);
        double quality =
                Math.min(
                        1,
                        (Math.min(1, (double) text.length() / Math.max(minimumCharacters, 1)) * 0.5)
                                + (printable * 0.3)
                                + (anchors * 0.2));
        return new ExtractedPage(pageNo, route, text, text.length(), printable, quality, hash(text));
    }

    private boolean quality(String text) {
        return text.length() >= minimumCharacters
                && printableRatio(text) >= minimumPrintableRatio
                && anchorScore(text) > 0;
    }

    private static double anchorScore(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int matches = 0;
      for (String anchor :
              List.of(
                        "教育", "经历", "项目", "技能", "求职意向", "education", "experience", "project", "skill", "@", "电话", "手机")) {
        if (lower.contains(anchor)) {
          matches++;
        }
      }
        return Math.min(1, matches / 2.0);
    }

    private static double printableRatio(String text) {
      if (text.isEmpty()) {
        return 0;
      }
        long printable = text.codePoints().filter(ResumeExtractedTextCleaner::isReadableCodePoint).count();
        return (double) printable / text.codePointCount(0, text.length());
    }

    private static String aggregateRoute(List<ExtractedPage> pages) {
        List<ExtractedPage> contentPages = pages.stream().filter(page -> !BLANK_PAGE.equals(page.route())).toList();
        List<String> routes = contentPages.stream()
                .map(page -> NATIVE_TEXT_VISUAL_LIMIT.equals(page.route()) ? "NATIVE_TEXT" : page.route())
                .distinct().toList();
        return routes.size() > 1 ? "HYBRID" : routes.getFirst();
    }

    /** 将压缩、抗锯齿产生的近白背景视为空白，但任何明显文字或图形像素仍进入识别链路。 */
    private static boolean isVisuallyBlank(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) return false;
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    if (((rgb >>> 16) & 0xFF) < BLANK_PAGE_MINIMUM_CHANNEL
                            || ((rgb >>> 8) & 0xFF) < BLANK_PAGE_MINIMUM_CHANNEL
                            || (rgb & 0xFF) < BLANK_PAGE_MINIMUM_CHANNEL) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            image.flush();
        }
    }

    /**
     * 续页可能只残留模板页眉页脚、局部装饰或极淡水印。仅在两层识别都没有得到有效文字后，
     * 允许把完全局限于页边距或占比极低且集中在小区域内的标记视为空白；首页以及跨越正文区域的
     * 浅色内容仍保持严格失败。
     */
    private static boolean isDecoratedBlankPage(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) return false;
        try {
            long markedPixels = 0;
            int minimumX = image.getWidth();
            int minimumY = image.getHeight();
            int maximumX = -1;
            int maximumY = -1;
            int edgeMargin = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 100);
            boolean edgeOnly = true;
            long maximumMarkedPixels = Math.max(64, (long) image.getWidth() * image.getHeight() / 2_500);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    if (((rgb >>> 16) & 0xFF) >= BLANK_PAGE_MINIMUM_CHANNEL
                            && ((rgb >>> 8) & 0xFF) >= BLANK_PAGE_MINIMUM_CHANNEL
                            && (rgb & 0xFF) >= BLANK_PAGE_MINIMUM_CHANNEL) {
                        continue;
                    }
                    markedPixels++;
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                    if (x >= edgeMargin
                            && x < image.getWidth() - edgeMargin
                            && y >= edgeMargin
                            && y < image.getHeight() - edgeMargin) {
                        edgeOnly = false;
                    }
                }
            }
            if (markedPixels == 0) return true;
            int markedWidth = maximumX - minimumX + 1;
            int markedHeight = maximumY - minimumY + 1;
            boolean marginOnly = maximumY < image.getHeight() * 6 / 100
                    || minimumY >= image.getHeight() * 94 / 100;
            long markedBoundsArea = (long) markedWidth * markedHeight;
            boolean topTemplateBand = maximumY < image.getHeight() * 8 / 100
                    && markedHeight <= Math.max(1, image.getHeight() * 4 / 100)
                    && markedWidth >= image.getWidth() / 2
                    && markedPixels * 4 >= markedBoundsArea;
            boolean sparseLocalDecoration = markedWidth <= Math.max(1, image.getWidth() * 3 / 100)
                    && markedHeight <= Math.max(1, image.getHeight() * 6 / 100)
                    && markedPixels <= maximumMarkedPixels;
            return marginOnly || topTemplateBand || edgeOnly || sparseLocalDecoration;
        } finally {
            image.flush();
        }
    }

    private static String clean(String text) {
        return ResumeExtractedTextCleaner.clean(text);
    }

    private static String hash(String text) {
        return Sha256Utils.hashUtf8(text);
    }

    private boolean pageQuality(String text, boolean continuation) {
        if (quality(text)) return true;
        // 已有有效首页时，后续页可能只有证书或职责的续行；不能要求每页重复简历标题。
        // 仍排除纯编号、散碎水印和不可读字符，不把低质量首页当作可用文档。
        return continuation && printableRatio(text) >= minimumPrintableRatio
                && (text.codePoints().filter(v -> Character.UnicodeScript.of(v) == Character.UnicodeScript.HAN).count() >= 4
                    || CONTINUATION_WORDS.matcher(text).find());
    }

    private static boolean isPdfBudgetFailure(IOException error) {
        String message = error.getMessage();
        return message != null && (message.startsWith(DOCUMENT_CONTAINER_LIMIT)
                || message.startsWith(DOCUMENT_CONTAINER_INVALID)
                || message.startsWith(DOCUMENT_TEXT_LIMIT)
                || message.startsWith("DOCUMENT_PAGE_RENDER_LIMIT"));
    }

    /** 对本次内存文档预解码通用流；不能调用会先缓存全部解压结果的 COSStream.createInputStream。 */
    private final class PdfResourceBudget {
        private final Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<COSBase> resourceNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<COSStream> preparedStreams = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<COSStream> validatedInlineStreams = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<COSStream, List<Operator>> inlineImages = new IdentityHashMap<>();
        private final Set<COSDictionary> checkedMasks = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<COSDictionary> reservedImages = Collections.newSetFromMap(new IdentityHashMap<>());
        private long expandedBytes;
        private long contentTokens;
        private int maskVisits;

        private void validatePage(PDPage page, int pageNo, boolean rendering) throws IOException {
            // 视觉失败不能缓存为验证成功；每次渲染重新遍历，成功解压和实际预算仍按文档共享。
            Set<COSBase> passVisited = rendering
                    ? Collections.newSetFromMap(new IdentityHashMap<>()) : visited;
            Set<COSStream> contents = Collections.newSetFromMap(new IdentityHashMap<>());
            Iterator<PDStream> streams = page.getContentStreams();
            while (streams.hasNext()) {
                COSStream stream = streams.next().getCOSObject();
                if (!rendering) requireTextDependency(stream);
                contents.add(stream);
            }
            Deque<COSBase> pending = new ArrayDeque<>();
            Set<COSBase> enqueued = Collections.newSetFromMap(new IdentityHashMap<>());
            enqueue(pending, page.getCOSObject(), enqueued);
            if (page.getResources() != null) {
                if (!rendering) requireTextDependency(page.getResources().getCOSObject());
                enqueue(pending, page.getResources().getCOSObject(), enqueued);
            }
            while (!pending.isEmpty()) {
                COSBase value = pending.removeFirst();
                // 文字角色由父引用决定，不能被先访问的同一个 Image 资源或 visited 去重遮掉。
                if (!rendering && value instanceof COSDictionary dictionary
                        && !(dictionary instanceof COSStream stream
                            && COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE)))) {
                    validateTextDependencies(dictionary);
                }
                if (!passVisited.add(value)) {
                    continue;
                }
                if (value instanceof COSObject reference) {
                    enqueue(pending, reference.getObject(), enqueued);
                } else if (value instanceof COSDictionary dictionary) {
                    if (dictionary instanceof COSStream stream) {
                        if (!rendering && COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE))) {
                            continue;
                        }
                        boolean contentStream = contents.contains(stream)
                                || COSName.FORM.equals(stream.getCOSName(COSName.SUBTYPE))
                                || stream.getInt(COSName.PATTERN_TYPE) == 1;
                        prepareStream(stream, pageNo, contentStream, rendering);
                    }
                    if (dictionary.size() > maximumPdfResourceNodes) throw new DocumentContainerLimitException();
                    for (var entry : dictionary.entrySet()) {
                        if (COSName.CHAR_PROCS.equals(entry.getKey())
                                && dictionary.getDictionaryObject(COSName.CHAR_PROCS) instanceof COSDictionary glyphs) {
                            for (COSBase glyph : glyphs.getValues()) {
                                COSBase direct = glyph instanceof COSObject reference ? reference.getObject() : glyph;
                                if (direct instanceof COSStream stream) {
                                    contents.add(stream);
                                }
                            }
                        }
                        // 页树 Parent 不是页面处理依赖；继承资源已通过 page.getResources() 单独加入。
                        if (!COSName.PARENT.equals(entry.getKey())) {
                            enqueue(pending, entry.getValue(), enqueued);
                        }
                    }
                } else if (value instanceof COSArray array) {
                    if (array.size() > maximumPdfResourceNodes) throw new DocumentContainerLimitException();
                    for (int index = 0; index < array.size(); index++) {
                        enqueue(pending, array.get(index), enqueued);
                    }
                }
            }
        }

        private void validateTextDependencies(COSDictionary dictionary) throws IOException {
            for (COSName key : TEXT_DEPENDENCIES) {
                COSBase dependency = requireTextDependency(dictionary.getItem(key));
                if (TEXT_RESOURCE_MAPS.contains(key) && dependency instanceof COSDictionary resources) {
                    if (resources.size() > maximumPdfResourceNodes) throw new DocumentContainerLimitException();
                    for (COSBase resource : resources.getValues()) requireTextDependency(resource);
                }
            }
        }

        private COSBase requireTextDependency(COSBase value) throws IOException {
            COSBase direct = directTextDependency(value);
            if (direct instanceof COSArray array) {
                if (array.size() > maximumPdfResourceNodes) throw new DocumentContainerLimitException();
                for (int index = 0; index < array.size(); index++) directTextDependency(array.get(index));
            }
            return direct;
        }

        private COSBase directTextDependency(COSBase value) throws IOException {
            int references = 0;
            while (value instanceof COSObject reference) {
                if (++references > maximumPdfResourceNodes) throw new DocumentContainerLimitException();
                value = reference.getObject();
            }
            if (value instanceof COSStream stream && COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE))) {
                throw new IOException(DOCUMENT_CONTAINER_INVALID);
            }
            return value;
        }

        private void enqueue(Deque<COSBase> pending, COSBase value, Set<COSBase> enqueued) throws IOException {
            if ((value instanceof COSObject || value instanceof COSDictionary || value instanceof COSArray)
                    && enqueued.add(value)) {
                // 文档内按对象身份计数；入队即去重，防止共享引用或环反复占用预算和队列。
                if (!resourceNodes.contains(value)) {
                    if (resourceNodes.size() >= maximumPdfResourceNodes) throw new DocumentContainerLimitException();
                    resourceNodes.add(value);
                }
                pending.addLast(value);
            }
        }

        private void prepareStream(COSStream stream, int pageNo, boolean contentStream, boolean rendering)
                throws IOException {
            if (preparedStreams.contains(stream)) {
                if (rendering && contentStream) validateInlineImages(stream, pageNo);
                return;
            }
            boolean image = COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE)) || checkedMasks.contains(stream);
            if (image) {
                validateMaskGraph(stream, Collections.newSetFromMap(new IdentityHashMap<>()), pageNo, 0);
                reserveImage(stream, pageNo);
            }
            byte[] raw;
            try (InputStream input = stream.createRawInputStream()) {
                raw = input.readNBytes(Math.toIntExact(MAX_FILE_SIZE_BYTES + 1));
                if (raw.length > MAX_FILE_SIZE_BYTES) {
                    throw new DocumentContainerLimitException();
                }
            }
            byte[] decoded = decode(stream, raw, image, pageNo, false);
            if (decoded == null) {
                preparedStreams.add(stream);
                return; // 头部尺寸已验证的图像 codec 保留编码及其 PDFBox 颜色空间语义。
            }
            try (OutputStream output = stream.createRawOutputStream()) {
                output.write(decoded);
            }
            stream.removeItem(COSName.FILTER);
            stream.removeItem(COSName.DECODE_PARMS);
            if (contentStream) {
                inspectContent(stream, decoded);
            }
            preparedStreams.add(stream);
            if (rendering && contentStream) validateInlineImages(stream, pageNo);
        }

        private void validateMaskGraph(COSDictionary image, Set<COSDictionary> active, int pageNo, int depth)
                throws IOException {
            if (active.contains(image)) {
                throw new IOException(DOCUMENT_CONTAINER_INVALID);
            }
            if (checkedMasks.contains(image)) {
                return;
            }
            if (++maskVisits > maximumPdfResourceNodes || depth >= MAXIMUM_PDF_GRAPHICS_DEPTH) {
                throw new DocumentContainerLimitException();
            }
            active.add(image);
            reserveImage(image, pageNo);
            for (COSName key : List.of(COSName.SMASK, COSName.MASK)) {
                if (image.getDictionaryObject(key) instanceof COSDictionary mask) {
                    validateMaskGraph(mask, active, pageNo, depth + 1);
                }
            }
            active.remove(image);
            checkedMasks.add(image);
        }

        private void reserveImage(COSDictionary image, int pageNo) throws IOException {
            validateImageDimensions(image, pageNo);
            // 即使 1-bit/高度压缩源流很小，RGB/蒙版合成仍会创建整幅栅格并由资源缓存持有。
            if (!reservedImages.contains(image)) {
                consumeBytes((long) imageWidth(image) * imageHeight(image) * 4);
                reservedImages.add(image);
            }
        }

        private void validateImageDimensions(COSDictionary image, int pageNo) throws IOException {
            validateImageDimensions(imageWidth(image), imageHeight(image), pageNo);
        }

        private void validateImageDimensions(int width, int height, int pageNo) throws IOException {
            if (width <= 0 || height <= 0 || width > maximumSourceImageDimension
                    || height > maximumSourceImageDimension || width > maximumSourceImagePixels / height) {
                throw renderLimit(pageNo);
            }
        }

        private int imageWidth(COSDictionary image) {
            return image.getInt(COSName.WIDTH, COSName.W, 0);
        }

        private int imageHeight(COSDictionary image) {
            return image.getInt(COSName.HEIGHT, COSName.H, 0);
        }

        private byte[] decode(COSDictionary dictionary, byte[] raw, boolean image, int pageNo, boolean inline)
                throws IOException {
            List<COSName> filters = filters(dictionary, inline);
            byte[] value = raw;
            if (filters.isEmpty()) {
                consumeBytes(value.length);
            }
            for (int index = 0; index < filters.size(); index++) {
                COSName filter = filters.get(index);
                COSDictionary parameters = decodeParameters(dictionary, index, inline);
                validateDecodeParameters(filter, parameters, dictionary, image, pageNo);
                if (isImageCodec(filter)) {
                    if (!image || index != filters.size() - 1) {
                        throw new IOException(DOCUMENT_CONTAINER_INVALID);
                    }
                    validateCodecHeader(value, dictionary, pageNo);
                    // 清除已安全解码的外层通用过滤器，否则 PDFBox 会对预解码数据重复执行这些过滤器。
                    if (!inline && index > 0 && dictionary instanceof COSStream stream) {
                        try (OutputStream output = stream.createRawOutputStream()) {
                            output.write(value);
                        }
                        stream.setItem(COSName.FILTER, filter);
                        stream.setItem(COSName.DECODE_PARMS, parameters);
                    }
                    return null;
                }
                if (!Set.of("FlateDecode", "Fl", "LZWDecode", "LZW", "ASCII85Decode", "A85",
                        "ASCIIHexDecode", "AHx", "RunLengthDecode", "RL", "CCITTFaxDecode", "CCF")
                        .contains(filter.getName())) {
                    throw new IOException(DOCUMENT_CONTAINER_INVALID);
                }
                try (InputStream input = new ByteArrayInputStream(value);
                        BoundedPdfOutput output = new BoundedPdfOutput()) {
                    FilterFactory.INSTANCE.getFilter(filter).decode(input, output, dictionary, index);
                    value = output.value();
                }
            }
            return value;
        }

        private List<COSName> filters(COSDictionary dictionary, boolean inline) throws IOException {
            COSBase value = inline ? dictionary.getDictionaryObject(COSName.FILTER, COSName.F)
                    : dictionary.getDictionaryObject(COSName.FILTER);
            if (value == null) {
                return List.of();
            }
            if (value instanceof COSName name) {
                return List.of(name);
            }
            if (value instanceof COSArray array) {
                if (array.size() > maximumPdfResourceNodes) {
                    throw new DocumentContainerLimitException();
                }
                List<COSName> names = new ArrayList<>();
                for (int index = 0; index < array.size(); index++) {
                    if (!(array.getObject(index) instanceof COSName name)) {
                        throw new IOException(DOCUMENT_CONTAINER_INVALID);
                    }
                    names.add(name);
                }
                return names;
            }
            throw new IOException(DOCUMENT_CONTAINER_INVALID);
        }

        private COSDictionary decodeParameters(COSDictionary dictionary, int index, boolean inline) {
            COSBase value = inline ? dictionary.getDictionaryObject(COSName.DECODE_PARMS, COSName.DP)
                    : dictionary.getDictionaryObject(COSName.DECODE_PARMS);
            if (value instanceof COSArray array) {
                value = index < array.size() ? array.getObject(index) : null;
            }
            return value instanceof COSDictionary parameters ? parameters : new COSDictionary();
        }

        private void validateDecodeParameters(COSName filter, COSDictionary parameters,
                COSDictionary dictionary, boolean image, int pageNo) throws IOException {
            if (parameters.getInt(COSName.PREDICTOR, 1) > 1) {
                int columns = parameters.getInt(COSName.COLUMNS, 1);
                int colors = parameters.getInt(COSName.COLORS, 1);
                int bits = parameters.getInt(COSName.BITS_PER_COMPONENT, 8);
                long rowBytes = ((long) columns * colors * bits + 7) / 8;
                if (columns <= 0 || columns > maximumSourceImageDimension || colors <= 0 || colors > 32
                        || !Set.of(1, 2, 4, 8, 16).contains(bits) || rowBytes > remainingBytes()) {
                    throw new DocumentContainerLimitException();
                }
            }
            if (Set.of("CCITTFaxDecode", "CCF").contains(filter.getName())) {
                if (!image) {
                    throw new IOException(DOCUMENT_CONTAINER_INVALID);
                }
                int columns = parameters.getInt(COSName.COLUMNS, 1728);
                int rows = imageHeight(dictionary);
                validateImageDimensions(columns, rows, pageNo);
                if (((long) columns + 7) / 8 * rows > remainingBytes()) {
                    throw new DocumentContainerLimitException();
                }
            }
        }

        private boolean isImageCodec(COSName filter) {
            return Set.of("DCTDecode", "DCT", "JPXDecode", "JBIG2Decode").contains(filter.getName());
        }

        private void validateCodecHeader(byte[] encoded, COSDictionary dictionary, int pageNo)
                throws IOException {
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new IOException(DOCUMENT_CONTAINER_INVALID);
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    validateImageDimensions(width, height, pageNo);
                    if (width != imageWidth(dictionary) || height != imageHeight(dictionary)) {
                        throw renderLimit(pageNo);
                    }
                } finally {
                    reader.dispose();
                }
            }
        }

        private void inspectContent(COSStream stream, byte[] content) throws IOException {
            PDFStreamParser parser = new PDFStreamParser(content);
            List<Operator> images = new ArrayList<>();
            try {
                Object token;
                while ((token = parser.parseNextToken()) != null) {
                    if (++contentTokens > (long) maximumExtractedCharacters + PDF_CONTENT_TOKEN_OVERHEAD) {
                        throw new DocumentContainerLimitException();
                    }
                    if (token instanceof Operator operator && operator.getImageParameters() != null) {
                        images.add(operator);
                    }
                }
            } finally {
                parser.close();
            }
            if (!images.isEmpty()) inlineImages.put(stream, List.copyOf(images));
        }

        private void validateInlineImages(COSStream stream, int pageNo) throws IOException {
            if (validatedInlineStreams.contains(stream)) return;
            for (Operator operator : inlineImages.getOrDefault(stream, List.of())) {
                COSDictionary image = operator.getImageParameters();
                reserveImage(image, pageNo);
                decode(image, operator.getImageData(), true, pageNo, true);
            }
            validatedInlineStreams.add(stream);
        }

        private long remainingBytes() {
            return maximumPdfExpandedBytes - expandedBytes;
        }

        private void consumeBytes(long bytes) throws IOException {
            if (bytes < 0 || bytes > remainingBytes()) {
                throw new DocumentContainerLimitException();
            }
            expandedBytes += bytes;
        }

        private final class BoundedPdfOutput extends OutputStream {
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();

            @Override
            public void write(int value) throws IOException {
                consumeBytes(1);
                output.write(value);
            }

            @Override
            public void write(byte[] value, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, value.length);
                consumeBytes(length);
                output.write(value, offset, length);
            }

            private byte[] value() {
                return output.toByteArray();
            }
        }
    }

    /** 限制动态 Form 展开和 path/graphics-state 累积，避免小资源图反复递归渲染。 */
    private static final class BudgetedPdfRenderer extends PDFRenderer {
        private final long maximumOperators;
        private final int maximumGraphicsDepth;
        private long processedOperators;

        private BudgetedPdfRenderer(PDDocument document, long maximumOperators, int maximumGraphicsDepth) {
            super(document);
            this.maximumOperators = maximumOperators;
            this.maximumGraphicsDepth = maximumGraphicsDepth;
        }

        @Override
        protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
            PageDrawer drawer = new PageDrawer(parameters) {
                @Override
                protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                    if (++processedOperators > maximumOperators
                            || "q".equals(operator.getName()) && getGraphicsStackSize() >= maximumGraphicsDepth) {
                        throw new DocumentContainerLimitException();
                    }
                    super.processOperator(operator, operands);
                }

                @Override
                protected void operatorException(Operator operator, List<COSBase> operands, IOException error)
                        throws IOException {
                    if (isPdfBudgetFailure(error)) {
                        throw error;
                    }
                    super.operatorException(operator, operands, error);
                }
            };
            drawer.setAnnotationFilter(getAnnotationsFilter());
            return drawer;
        }
    }

    /** 输出文本预算与解码、字形预算分别执行，防止清洗/去重掩盖已消耗的解析资源。 */
    private static final class BoundedTextWriter extends Writer {

        private final int maximumCharacters;
        private final String limitMessage;
        private final StringBuilder value;

        private BoundedTextWriter(int maximumCharacters, String limitMessage) {
            this.maximumCharacters = maximumCharacters;
            this.limitMessage = limitMessage;
            this.value = new StringBuilder(Math.min(maximumCharacters, 8_192));
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            requireCapacity(length);
            value.append(buffer, offset, length);
        }

        @Override
        public void write(int character) throws IOException {
            requireCapacity(1);
            value.append((char) character);
        }

        @Override
        public void flush() {
            // 纯内存接收器没有待刷新的外部资源。
        }

        @Override
        public void close() {
            // 生命周期由单页解析方法持有，不占用外部资源。
        }

        private void requireCapacity(int requestedCharacters) throws IOException {
            if (requestedCharacters > maximumCharacters - value.length()) {
                throw new IOException(limitMessage);
            }
        }

        private String value() {
            return value.toString();
        }
    }

    private static final class DocumentContainerLimitException extends IOException {

        private DocumentContainerLimitException() {
            super(DOCUMENT_CONTAINER_LIMIT);
        }

        private DocumentContainerLimitException(Throwable cause) {
            super(DOCUMENT_CONTAINER_LIMIT, cause);
        }
    }
}
