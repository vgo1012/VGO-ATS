package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import java.awt.Font;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyReportChartFontsTest {

    @Test
    void loadsBundledChineseFontAndCircledNumbersWithoutSystemFontLookup() {
        Font font = MonthlyReportChartFonts.deriveFont(Font.PLAIN, 18);

        assertThat(font.getFamily()).isEqualTo("Noto Sans CJK SC");
        assertThat(font.canDisplayUpTo("招聘月度汇报部门发送人已接受已入职已拒绝①②③④⑤⑥⑦⑧⑨⑩−—")).isEqualTo(-1);
        assertThat(font.getSize2D()).isEqualTo(18);
        assertThat(font.isBold()).isFalse();
    }

    @Test
    void derivesBoldAndDifferentSizesWithoutMutatingBaseFont() {
        Font bold = MonthlyReportChartFonts.deriveFont(Font.BOLD, 30);
        Font plain = MonthlyReportChartFonts.deriveFont(Font.PLAIN, 14);

        assertThat(bold.isBold()).isTrue();
        assertThat(bold.getSize2D()).isEqualTo(30);
        assertThat(plain.isBold()).isFalse();
        assertThat(plain.getSize2D()).isEqualTo(14);
    }

    @Test
    void rejectsInvalidFontSizes() {
        for (float size : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> MonthlyReportChartFonts.deriveFont(Font.PLAIN, size))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
