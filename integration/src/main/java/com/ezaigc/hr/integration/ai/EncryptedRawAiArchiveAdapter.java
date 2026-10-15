package com.ezaigc.hr.integration.ai;

import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class EncryptedRawAiArchiveAdapter implements RawAiArchivePort {
  private static final byte[] HEADER = "EZAIGC-AI-1".getBytes(StandardCharsets.US_ASCII);
  @Resource private ObjectStoragePort storage;
  @Resource private IntegrationProperties properties;
  /** 键。 */
  private SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  @PostConstruct
  void initializeEncryptionKey() {
    String base64Key = properties.getAi().getArchiveKey();
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("hr.ai.archive-key must be base64", ex);
    }
    if (decoded.length != 32)
      throw new IllegalStateException("hr.ai.archive-key must decode to 32 bytes");
    key = new SecretKeySpec(decoded, "AES");
  }

  @Override
  public ArchiveResult archive(String objectKey, String rawJson) throws IOException {
    try {
      byte[] compressed = gzip(rawJson);
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(compressed);
      ByteArrayOutputStream payload = new ByteArrayOutputStream();
      payload.write(HEADER);
      payload.write(nonce);
      payload.write(encrypted);
      byte[] bytes = payload.toByteArray();
      storage.put(objectKey, new ByteArrayInputStream(bytes));
      return new ArchiveResult(objectKey, Sha256Utils.hashBytes(bytes), bytes.length, "AES-256-GCM+GZIP/v1");
    } catch (GeneralSecurityException ex) {
      throw new IOException("AI archive encryption failed", ex);
    }
  }

  private static byte[] gzip(String value) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
      gzip.write(value.getBytes(StandardCharsets.UTF_8));
    }
    return output.toByteArray();
  }

}
