package com.ezaigc.hr.integration.storage;

import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.InputStream;

final class ResumeContentSignature {
    private static final int MAX_DOCX_ENTRY_COUNT = 10_000;
    /** 前置识别与现有文档容器边界一致，默认最多展开 64MiB。 */
    private static final long MAX_DOCX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
    /** 仅对 XLSX 解压校验设置额外上限，避免受限压缩输入放大占用扫描进程内存。 */
    static final int MAX_XLSX_ENTRY_COUNT = 10_000;
    private static final long MAX_XLSX_ENTRY_BYTES = 32L * 1024 * 1024;
    static final long MAX_XLSX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
    private static final String EXCEL_MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";

    private ResumeContentSignature() {}

    static boolean matches(String fileName, byte[] content) throws IOException {
        return matches(fileName, content, MAX_DOCX_UNCOMPRESSED_BYTES, MAX_DOCX_ENTRY_COUNT);
    }

    static boolean matches(String fileName, byte[] content, long maximumDocxExpandedBytes,
            int maximumDocxEntries) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return startsWith(content, new int[] {'%', 'P', 'D', 'F', '-'});
        }
        if (lower.endsWith(".doc")) {
            return startsWith(content, new int[] {0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1});
        }
        if (lower.endsWith(".docx")) {
            return isWordOpenXml(content, maximumDocxExpandedBytes, maximumDocxEntries);
        }
        if (lower.endsWith(".xlsx")) {
            return isExcelOpenXml(content);
        }
        return false;
    }

    static byte[] readBounded(InputStream content) throws IOException {
        byte[] bytes = content.readNBytes(Math.toIntExact(MAX_FILE_SIZE_BYTES + 1));
        if (bytes.length > MAX_FILE_SIZE_BYTES) throw new IOException("resume exceeds scan size limit");
        return bytes;
    }

    private static boolean startsWith(byte[] content, int[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    private static boolean isWordOpenXml(byte[] content, long maximumExpandedBytes,
            int maximumEntries) {
        boolean contentTypes = false;
        boolean wordDocument = false;
        long totalExpandedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int entries = 0;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > maximumEntries) return false;
                // 显式有界消费每个部件，避免 getNextEntry 隐式解压，也不因必需名称齐全而跳过后续部件。
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    if (read > maximumExpandedBytes - totalExpandedBytes) return false;
                    totalExpandedBytes += read;
                }
                String name = entry.getName();
                contentTypes |= "[Content_Types].xml".equals(name);
                wordDocument |= "word/document.xml".equals(name);
            }
        } catch (IOException malformedArchive) {
            // 容器已读入内存；部件损坏属于非法输入，不能降级为扫描服务暂不可用。
            return false;
        }
        return contentTypes && wordDocument;
    }

    /**
     * XLSX 是 ZIP 容器，除确认 OOXML 必需部件外，还拒绝宏、外部关系和会使扫描器过度解压的
     * 目录结构。这里的 false 表示输入本身不安全或不符合 XLSX 声明，应由适配器映射为
     * {@code MALICIOUS}；只有读取流或 ClamAV 通讯失败才应产生 {@code UNAVAILABLE}。
     */
    private static boolean isExcelOpenXml(byte[] content) {
        boolean contentTypes = false;
        boolean rootRelationships = false;
        boolean workbook = false;
        boolean spreadsheetContentType = false;
        long totalUncompressed = 0;
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int entries = 0;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_XLSX_ENTRY_COUNT || !isSafeZipEntryName(entry.getName(), entry.isDirectory())
                        || !names.add(entry.getName())) {
                    return false;
                }
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (isForbiddenExcelPart(lower)) {
                    return false;
                }
                long entryBytes = 0;
                ByteArrayOutputStream text = isRelationshipOrContentType(lower)
                        ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    totalUncompressed += read;
                    if (entryBytes > MAX_XLSX_ENTRY_BYTES
                            || totalUncompressed > MAX_XLSX_UNCOMPRESSED_BYTES) {
                        return false;
                    }
                    if (text != null) {
                        text.write(buffer, 0, read);
                    }
                }
                if ("[Content_Types].xml".equals(name)) {
                    contentTypes = true;
                    spreadsheetContentType = containsAsciiIgnoreCase(text.toByteArray(), EXCEL_MAIN_CONTENT_TYPE);
                } else if ("_rels/.rels".equals(name)) {
                    rootRelationships = true;
                } else if ("xl/workbook.xml".equals(name)) {
                    workbook = true;
                }
                if (lower.endsWith(".rels") && containsExternalRelationship(text.toByteArray())) {
                    return false;
                }
            }
        } catch (IOException malformedArchive) {
            // 容器字节已完整读入；这里的 IOException 表示 ZIP 损坏，不是扫描服务暂不可用。
            return false;
        }
        return contentTypes && rootRelationships && workbook && spreadsheetContentType;
    }

    private static boolean isSafeZipEntryName(String value, boolean directory) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("\\")) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (directory && index == segments.length - 1 && segment.isEmpty()) {
                continue;
            }
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isForbiddenExcelPart(String lowerName) {
        return lowerName.equals("xl/vbaproject.bin")
                || lowerName.startsWith("xl/activex/")
                || lowerName.startsWith("xl/externallinks/")
                || lowerName.startsWith("xl/embeddings/");
    }

    private static boolean isRelationshipOrContentType(String lowerName) {
        return lowerName.endsWith(".rels") || "[content_types].xml".equals(lowerName);
    }

    private static boolean containsExternalRelationship(byte[] content) {
        byte[] targetMode = "targetmode".getBytes(StandardCharsets.US_ASCII);
        byte[] external = "external".getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= content.length - targetMode.length; offset++) {
            if (!matchesAsciiIgnoreCase(content, offset, targetMode)) {
                continue;
            }
            int cursor = skipAsciiWhitespace(content, offset + targetMode.length);
            if (cursor >= content.length || content[cursor] != '=') {
                continue;
            }
            cursor = skipAsciiWhitespace(content, cursor + 1);
            if (cursor < content.length && (content[cursor] == '\'' || content[cursor] == '\"')) {
                cursor++;
            }
            if (cursor <= content.length - external.length
                    && matchesAsciiIgnoreCase(content, cursor, external)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAsciiIgnoreCase(byte[] content, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= content.length - expectedBytes.length; offset++) {
            if (matchesAsciiIgnoreCase(content, offset, expectedBytes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAsciiIgnoreCase(byte[] content, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            int actual = content[offset + index] & 0xff;
            int wanted = expected[index] & 0xff;
            if (actual >= 'A' && actual <= 'Z') {
                actual += 'a' - 'A';
            }
            if (actual != wanted) {
                return false;
            }
        }
        return true;
    }

    private static int skipAsciiWhitespace(byte[] content, int offset) {
        int cursor = offset;
        while (cursor < content.length && (content[cursor] == ' ' || content[cursor] == '\t'
                || content[cursor] == '\n' || content[cursor] == '\r')) {
            cursor++;
        }
        return cursor;
    }
}
