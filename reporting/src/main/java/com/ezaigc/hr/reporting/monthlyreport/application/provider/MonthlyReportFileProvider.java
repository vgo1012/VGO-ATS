package com.ezaigc.hr.reporting.monthlyreport.application.provider;

import com.ezaigc.hr.common.core.util.Sha256Utils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.web.multipart.MultipartFile;

/** 月报文件暂存及私有对象存储的外部能力边界。 */
public interface MonthlyReportFileProvider {
    StagedFile stage(MultipartFile file) throws IOException;
    void put(String key, Path path) throws IOException;
    Path fetch(String key, String expectedHash, long expectedSize) throws IOException;
    void delete(String key) throws IOException;
    InputStream open(String key) throws IOException;
    static String hash(byte[] bytes) {
        return Sha256Utils.hashBytes(bytes);
    }
    @Value
    @Accessors(fluent = true)
    class StagedFile implements AutoCloseable {
        Path path;
        String fileName;
        long sizeBytes;
        String hash;
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}
