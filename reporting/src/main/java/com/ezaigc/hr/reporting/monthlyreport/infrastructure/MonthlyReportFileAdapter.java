package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 有界磁盘暂存和私有对象 IO；调用者不得包在数据库事务中。 */
@Component
public class MonthlyReportFileAdapter implements MonthlyReportFileProvider {
    @Resource private ObjectStoragePort storage;
    @Resource private MonthlyReportProperties properties;

    public StagedFile stage(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null || name.length() > 200 || name.contains("/") || name.contains("\\")
                || name.codePoints().anyMatch(Character::isISOControl)
                || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx") || file.isEmpty())
            throw PlatformExceptions.badRequest("MONTHLY_REPORT_FILE_INVALID", "请上传文件名不超过200字的非空 xlsx 文件");
        if (file.getSize() > properties.getMaxFileBytes())
            throw PlatformExceptions.badRequest("MONTHLY_REPORT_FILE_TOO_LARGE", "单份 Excel 不能超过10MiB");
        Path path = Files.createTempFile("hr-monthly-upload-", ".xlsx");
        try {
            MessageDigest digest = digest();
            long size;
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest); OutputStream out = Files.newOutputStream(path)) {
                size = copyBounded(in, out, properties.getMaxFileBytes());
            }
            return new StagedFile(path, name, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException failure) {
            cleanupAfterFailure(path, failure);
            throw failure;
        }
    }

    public void put(String key, Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) { storage.put(key, in); }
    }
    public Path fetch(String key, String expectedHash, long expectedSize) throws IOException {
        Path path = Files.createTempFile("hr-monthly-input-", ".xlsx");
        try {
            MessageDigest digest = digest();
            long size;
            try (InputStream in = new DigestInputStream(storage.open(key), digest); OutputStream out = Files.newOutputStream(path)) {
                size = copyBounded(in, out, properties.getMaxFileBytes());
            }
            if (size != expectedSize || !HexFormat.of().formatHex(digest.digest()).equals(expectedHash))
                throw new IOException("月报输入完整性校验失败");
            return path;
        } catch (IOException | RuntimeException failure) {
            cleanupAfterFailure(path, failure);
            throw failure;
        }
    }
    public void delete(String key) throws IOException { storage.delete(key); }
    public InputStream open(String key) throws IOException { return storage.open(key); }
    private static void cleanupAfterFailure(Path path, Throwable failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static long copyBounded(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buffer = new byte[65536]; long total = 0; int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > max) throw new IOException("月报文件超过上限");
            out.write(buffer, 0, read);
        }
        return total;
    }
}
