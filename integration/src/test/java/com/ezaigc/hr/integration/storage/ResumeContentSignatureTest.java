package com.ezaigc.hr.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ResumeContentSignatureTest {
    @Test
    void requiresTheMagicBytesToMatchTheDeclaredExtension() throws Exception {
        assertThat(ResumeContentSignature.matches("resume.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(ResumeContentSignature.matches("resume.pdf", "PK fake zip".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ResumeContentSignature.matches("resume.docx", docx())).isTrue();
        assertThat(ResumeContentSignature.matches("resume.docx", zipWith("notes.txt"))).isFalse();
    }

    @Test
    void rejectsDocxWithTruncatedRequiredPart() throws Exception {
        byte[] document = docx();
        String entryName = "word/document.xml";
        int contentOffset = new String(document, StandardCharsets.ISO_8859_1).indexOf(entryName)
                + entryName.length();
        byte[] truncated = Arrays.copyOf(document, contentOffset);

        assertThat(ResumeContentSignature.matches("resume.docx", truncated)).isFalse();
    }

    @Test
    void boundsDocxExpandedBytesIncludingLeadingAndTrailingParts() throws Exception {
        byte[] document = docx(List.of("docProps/core.xml"), List.of("word/styles.xml"));
        long expandedBytes = "<Types/>".length() + "<document/>".length() + 2 * "<part/>".length();

        assertThat(ResumeContentSignature.matches("resume.docx", document, expandedBytes, 4)).isTrue();
        assertThat(ResumeContentSignature.matches("resume.docx", document, expandedBytes - 1, 4)).isFalse();
    }

    @Test
    void countsDocxEntriesAfterRequiredParts() throws Exception {
        byte[] document = docx(List.of(), List.of("word/styles.xml"));

        assertThat(ResumeContentSignature.matches("resume.docx", document, 64, 3)).isTrue();
        assertThat(ResumeContentSignature.matches("resume.docx", document, 64, 2)).isFalse();
    }

    @Test
    void acceptsBoundedMacroFreeXlsxButKeepsResumeExtensionsUnchanged() throws Exception {
        byte[] workbook = xlsx(List.of());

        assertThat(ResumeContentSignature.matches("monthly.xlsx", workbook)).isTrue();
        assertThat(ResumeContentSignature.matches("resume.docx", workbook)).isFalse();
    }

    @Test
    void rejectsXlsxWithMacroExternalLinkOrExternalRelationship() throws Exception {
        assertThat(ResumeContentSignature.matches("monthly.xlsx", xlsx(List.of("xl/vbaProject.bin")))).isFalse();
        assertThat(ResumeContentSignature.matches("monthly.xlsx", xlsx(List.of("xl/externalLinks/externalLink1.xml")))).isFalse();
        assertThat(ResumeContentSignature.matches("monthly.xlsx", xlsxWithRelationship(
                "<Relationships><Relationship TargetMode = \"External\" Target=\"https://example.test/a\"/></Relationships>"))).isFalse();
    }

    @Test
    void rejectsXlsxWithTooManyZipEntries() throws Exception {
        List<String> names = new ArrayList<>();
        for (int index = 0; index <= ResumeContentSignature.MAX_XLSX_ENTRY_COUNT; index++) {
            names.add("xl/worksheets/sheet" + index + ".xml");
        }

        assertThat(ResumeContentSignature.matches("monthly.xlsx", xlsx(names))).isFalse();
    }

    @Test
    void rejectsXlsxWhoseUncompressedPayloadExceedsTheBound() throws Exception {
        assertThat(ResumeContentSignature.matches("monthly.xlsx",
                xlsxWithLargeEntry(ResumeContentSignature.MAX_XLSX_UNCOMPRESSED_BYTES + 1))).isFalse();
    }

    private static byte[] docx() throws Exception {
        return docx(List.of(), List.of());
    }

    private static byte[] docx(List<String> leadingEntries, List<String> trailingEntries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String name : leadingEntries) put(zip, name, "<part/>");
            put(zip, "[Content_Types].xml", "<Types/>");
            put(zip, "word/document.xml", "<document/>");
            for (String name : trailingEntries) put(zip, name, "<part/>");
        }
        return output.toByteArray();
    }

    private static byte[] zipWith(String name) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(1);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] xlsx(List<String> extraEntries) throws Exception {
        return xlsxWithRelationship("<Relationships/>", extraEntries);
    }

    private static byte[] xlsxWithRelationship(String relationship) throws Exception {
        return xlsxWithRelationship(relationship, List.of());
    }

    private static byte[] xlsxWithRelationship(String relationship, List<String> extraEntries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "[Content_Types].xml", "<Types><Override ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>");
            put(zip, "_rels/.rels", relationship);
            put(zip, "xl/workbook.xml", "<workbook/>");
            for (String entry : extraEntries) {
                put(zip, entry, "<part/>");
            }
        }
        return output.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static byte[] xlsxWithLargeEntry(long size) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "[Content_Types].xml", "<Types><Override ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>");
            put(zip, "_rels/.rels", "<Relationships/>");
            put(zip, "xl/workbook.xml", "<workbook/>");
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            byte[] block = new byte[8192];
            for (long remaining = size; remaining > 0; remaining -= block.length) {
                zip.write(block, 0, Math.toIntExact(Math.min(remaining, block.length)));
            }
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
