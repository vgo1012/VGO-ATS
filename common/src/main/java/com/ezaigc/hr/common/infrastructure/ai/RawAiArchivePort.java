package com.ezaigc.hr.common.infrastructure.ai;

import java.io.IOException;
import lombok.Value;
import lombok.experimental.Accessors;

public interface RawAiArchivePort {

    ArchiveResult archive(String objectKey, String rawJson) throws IOException;

    @Value
    @Accessors(fluent = true)
    class ArchiveResult {

        String objectKey;
        String sha256;
        long sizeBytes;
        String encryptionVersion;
    }
}
