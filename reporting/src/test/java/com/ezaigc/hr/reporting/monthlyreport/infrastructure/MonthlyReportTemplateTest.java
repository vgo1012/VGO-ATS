package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class MonthlyReportTemplateTest {
    @Test
    void reusableReferenceContainsLayoutButNoBusinessContentOrExternalLinks() throws Exception {
        try (InputStream source = getClass().getResourceAsStream("/monthly-report/reference-template.docx")) {
            assertThat(source).isNotNull();
            try (XWPFDocument doc = new XWPFDocument(source)) {
                assertThat(doc.getBodyElements()).isEmpty();
                assertThat(doc.getAllPictures()).isEmpty();
                assertThat(doc.getStyles().getStyle("rdbvau").getName()).isEqualTo("Title");
                assertThat(doc.getDocument().getBody().getSectPr().getPgSz().getW().toString()).isEqualTo("11905");
                assertThat(doc.getDocument().getBody().getSectPr().getPgSz().getH().toString()).isEqualTo("16838");
            }
        }
        try (ZipInputStream zip = new ZipInputStream(getClass().getResourceAsStream("/monthly-report/reference-template.docx"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                assertThat(entry.getName()).doesNotStartWith("word/media/").doesNotStartWith("docProps/");
                if (entry.getName().endsWith(".rels")) {
                    assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).doesNotContain("External", "image");
                }
            }
        }
    }
}
