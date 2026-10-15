package com.ezaigc.hr.common.web.pagination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/** 使用 HMAC-SHA256 签名并绑定查询作用域的单段 Base64URL 游标。 */
public final class SignedCursorCodec implements CursorCodec {

  private static final int VERSION = 1;
  private static final int SIGNATURE_LENGTH = 32;
  private static final int MAX_PAYLOAD_LENGTH = 1400;
  private final byte[] key;

  public SignedCursorCodec(String signingKey) {
    Objects.requireNonNull(signingKey, "signingKey");
    if (signingKey.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("cursor signing key must contain at least 32 bytes");
    }
    this.key = signingKey.getBytes(StandardCharsets.UTF_8).clone();
  }

  @Override
  public String encode(String scope, List<String> values) {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(values, "values");
    try {
      ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(payloadBytes)) {
        output.writeByte(VERSION);
        output.writeUTF(scope);
        output.writeInt(values.size());
        for (String value : values) {
          output.writeUTF(Objects.requireNonNull(value, "cursor value"));
        }
      }
      byte[] payload = payloadBytes.toByteArray();
      if (payload.length > MAX_PAYLOAD_LENGTH) {
        throw new IllegalArgumentException("cursor payload is too large");
      }
      ByteArrayOutputStream token = new ByteArrayOutputStream(payload.length + SIGNATURE_LENGTH);
      token.write(payload);
      token.write(signature(payload));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray());
    } catch (IOException exception) {
      throw new IllegalStateException("failed to encode cursor", exception);
    }
  }

  @Override
  public List<String> decode(String token, String expectedScope, int expectedValueCount) {
    Objects.requireNonNull(expectedScope, "expectedScope");
    if (expectedValueCount < 0) {
      throw new IllegalArgumentException("expectedValueCount must not be negative");
    }
    try {
      byte[] combined = Base64.getUrlDecoder().decode(OpaqueCursor.requireValid(token));
      if (combined.length <= SIGNATURE_LENGTH) {
        throw new IllegalArgumentException("cursor payload is missing");
      }
      int payloadLength = combined.length - SIGNATURE_LENGTH;
      if (payloadLength > MAX_PAYLOAD_LENGTH) {
        throw new IllegalArgumentException("cursor payload is too large");
      }
      byte[] payload = Arrays.copyOf(combined, payloadLength);
      byte[] actualSignature = Arrays.copyOfRange(combined, payloadLength, combined.length);
      if (!MessageDigest.isEqual(signature(payload), actualSignature)) {
        throw new IllegalArgumentException("cursor signature is invalid");
      }
      try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
        if (input.readUnsignedByte() != VERSION || !expectedScope.equals(input.readUTF())) {
          throw new IllegalArgumentException("cursor scope is invalid");
        }
        int count = input.readInt();
        if (count != expectedValueCount) {
          throw new IllegalArgumentException("cursor value count is invalid");
        }
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          values.add(input.readUTF());
        }
        if (input.read() != -1) {
          throw new IllegalArgumentException("cursor payload has trailing data");
        }
        return List.copyOf(values);
      }
    } catch (IllegalArgumentException | IOException exception) {
      throw new IllegalArgumentException("invalid cursor", exception);
    }
  }

  private byte[] signature(byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HmacSHA256 is unavailable", exception);
    }
  }
}
