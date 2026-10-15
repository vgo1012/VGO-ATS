package com.ezaigc.hr.integration.document;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/** 排除近乎透明的斜向追踪水印，不按随机字符串模式删除简历事实。 */
final class ResumePdfTextStripper extends PDFTextStripper {
    private static final int MIN_FRAGMENTED_IMAGES = 20;
    private static final double MIN_FRAGMENTED_IMAGE_AREA_RATIO = 0.10;
    private final int maximumGlyphs;
    private final long maximumOperators;
    private final int maximumGraphicsDepth;
    private int processedGlyphs;
    private long processedOperators;
    private int pageNo;
    private int visibleGlyphs;
    private int mappedGlyphs;
    private boolean largeRasterLayer;
    private int fragmentedImages;
    private double fragmentedImageAreaRatio;

    ResumePdfTextStripper(int maximumGlyphs, long maximumOperators, int maximumGraphicsDepth)
            throws IOException {
        this.maximumGlyphs = maximumGlyphs;
        this.maximumOperators = maximumOperators;
        this.maximumGraphicsDepth = maximumGraphicsDepth;
        setSortByPosition(true);
    }

    void beginPage(int pageNo) {
        this.pageNo = pageNo;
        visibleGlyphs = 0;
        mappedGlyphs = 0;
        largeRasterLayer = false;
        fragmentedImages = 0;
        fragmentedImageAreaRatio = 0;
    }

    boolean hasUnmappedVisibleGlyphs() {
        return mappedGlyphs < visibleGlyphs;
    }

    boolean hasVisualTextLayer() {
        return largeRasterLayer || fragmentedImages >= MIN_FRAGMENTED_IMAGES
                && fragmentedImageAreaRatio >= MIN_FRAGMENTED_IMAGE_AREA_RATIO;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        if (++processedOperators > maximumOperators
                || "q".equals(operator.getName()) && getGraphicsStackSize() >= maximumGraphicsDepth) {
            throw new IOException("DOCUMENT_CONTAINER_LIMIT");
        }
        if ("Do".equals(operator.getName()) && !operands.isEmpty()
                && operands.getFirst() instanceof COSName name && getResources() != null
                && getResources().isImageXObject(name) && getCurrentPage() != null) {
            var crop = getCurrentPage().getCropBox();
            var matrix = getGraphicsState().getCurrentTransformationMatrix();
            if (crop != null && matrix != null) {
                double pageArea = (double) crop.getWidth() * crop.getHeight();
                double imageArea = Math.abs((double) matrix.getScaleX() * matrix.getScaleY()
                        - (double) matrix.getShearX() * matrix.getShearY());
                if (pageArea > 0) {
                    if (imageArea >= pageArea / 2) {
                        largeRasterLayer = true;
                    } else if (imageArea > 0) {
                        fragmentedImages++;
                        fragmentedImageAreaRatio += imageArea / pageArea;
                    }
                }
            }
        }
        super.processOperator(operator, operands);
    }

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement)
            throws IOException {
        // PDFBox 先累计/排序 TextPosition，再向 Writer 输出；去重后的文本长度不能约束这些对象。
        if (processedGlyphs >= maximumGlyphs) {
            throw new IOException("DOCUMENT_TEXT_LIMIT:" + pageNo);
        }
        processedGlyphs++;
        if (isTransparentDiagonalWatermark(textRenderingMatrix)) {
            return;
        }
        visibleGlyphs++;
        super.showGlyph(textRenderingMatrix, font, code, displacement);
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        mappedGlyphs++;
        if (isTransparentDiagonalWatermark(text.getTextMatrix())) {
            return;
        }
        super.processTextPosition(text);
    }

    @Override
    protected void operatorException(Operator operator, List<COSBase> operands, IOException error)
            throws IOException {
        // 默认引擎会忽略 Form 的 Do 异常，安全门禁必须穿透这一层。
        if (error.getMessage() != null && (error.getMessage().startsWith("DOCUMENT_TEXT_LIMIT")
                || error.getMessage().startsWith("DOCUMENT_CONTAINER_LIMIT"))) {
            throw error;
        }
        super.operatorException(operator, operands, error);
    }

    /** 在 PDFBox 查询字体 Unicode 映射前排除已确认的透明斜向追踪水印。 */
    private boolean isTransparentDiagonalWatermark(Matrix matrix) {
        double angle = Math.atan2(matrix.getShearY(), matrix.getScaleX());
        boolean diagonal = Math.abs(Math.sin(angle * 2)) > 0.1;
        // 保留正常旋转正文、横/竖排、以及 rendering mode NEITHER 的扫描件 OCR 隐藏层。
        return diagonal
                && getGraphicsState().getTextState().getRenderingMode() == RenderingMode.FILL
                && getGraphicsState().getNonStrokeAlphaConstant() <= 0.01;
    }
}
