package com.ezaigc.hr.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EncryptedRawAiArchiveAdapterTest {
  private static final byte[] KEY =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
  private static final int HEADER_LENGTH = "EZAIGC-AI-1".length();

  @Test
  void archivesCompressedAuthenticatedCiphertextThatCanBeRecovered() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    EncryptedRawAiArchiveAdapter adapter = adapter(storage, properties(KEY));
    String raw = "{\"candidate\":{\"fullName\":\"匿名候选人\"},\"evidence\":[]}";

    ArchiveResult result = adapter.archive("ai/run-1.bin", raw);

    byte[] payload = storage.values.get("ai/run-1.bin");
    assertThat(payload).isNotEqualTo(raw.getBytes(StandardCharsets.UTF_8));
    assertThat(new String(payload, 0, HEADER_LENGTH, StandardCharsets.US_ASCII))
        .isEqualTo("EZAIGC-AI-1");
    assertThat(result.encryptionVersion()).isEqualTo("AES-256-GCM+GZIP/v1");
    assertThat(result.sha256()).hasSize(64);
    assertThat(decrypt(payload)).isEqualTo(raw);
  }

  @Test
  void rejectsKeysThatAreNotExactlyAes256() {
    assertThatThrownBy(() -> adapter(new MemoryStorage(), properties(new byte[16])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  private static IntegrationProperties properties(byte[] key) {
    return new IntegrationProperties(
        null,
        null,
        new IntegrationProperties.Ai(Base64.getEncoder().encodeToString(key), null),
        null,
        null);
  }

  private static EncryptedRawAiArchiveAdapter adapter(
      ObjectStoragePort storage, IntegrationProperties properties) {
    EncryptedRawAiArchiveAdapter adapter = new EncryptedRawAiArchiveAdapter();
    ReflectionTestUtils.setField(adapter, "storage", storage);
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ReflectionTestUtils.invokeMethod(adapter, "initializeEncryptionKey");
    return adapter;
  }

  private static String decrypt(byte[] payload) throws Exception {
    byte[] nonce = Arrays.copyOfRange(payload, HEADER_LENGTH, HEADER_LENGTH + 12);
    byte[] ciphertext = Arrays.copyOfRange(payload, HEADER_LENGTH + 12, payload.length);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, nonce));
    try (GZIPInputStream gzip =
            new GZIPInputStream(new ByteArrayInputStream(cipher.doFinal(ciphertext)));
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      gzip.transferTo(output);
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  private static final class MemoryStorage implements ObjectStoragePort {
    private final Map<String, byte[]> values = new HashMap<>();

    @Override
    public void put(String objectKey, InputStream content) throws IOException {
      values.put(objectKey, content.readAllBytes());
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
      byte[] value = values.get(objectKey);
      if (value == null) throw new IOException("missing");
      return new ByteArrayInputStream(value);
    }

    @Override
    public boolean exists(String objectKey) {
      return values.containsKey(objectKey);
    }

    @Override
    public void delete(String objectKey) {
      values.remove(objectKey);
    }
  }
}
