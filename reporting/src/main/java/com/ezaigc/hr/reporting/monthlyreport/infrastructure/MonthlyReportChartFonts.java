package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/** 月报图片使用随应用发布的字体，避免部署机器字体不同导致缺字或布局漂移。 */
final class MonthlyReportChartFonts {

    private static final String RESOURCE = "/fonts/monthly-report/NotoSansCJKsc-Regular.otf";
    private static final String REQUIRED_GLYPHS = "招聘月度汇报部门发送人数量分值已入职接受拒绝①②③④⑤⑥⑦⑧⑨⑩0123456789%−—";
    private static final Font BASE_FONT = load();

    private MonthlyReportChartFonts() {
    }

    static Font deriveFont(int style, float size) {
        if (!Float.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("月报图表字号必须为有限正数");
        }
        return BASE_FONT.deriveFont(style, size);
    }

    private static Font load() {
        try (InputStream source = MonthlyReportChartFonts.class.getResourceAsStream(RESOURCE)) {
            if (source == null) {
                throw new IllegalStateException("月报图表内置字体资源缺失");
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, source);
            if (font.canDisplayUpTo(REQUIRED_GLYPHS) >= 0) {
                throw new IllegalStateException("月报图表内置字体缺少必要中文字形");
            }
            return font;
        } catch (FontFormatException | IOException exception) {
            throw new IllegalStateException("月报图表内置字体无法加载", exception);
        }
    }
}
