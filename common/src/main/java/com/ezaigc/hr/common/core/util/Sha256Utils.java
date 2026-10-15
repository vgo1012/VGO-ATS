package com.ezaigc.hr.common.core.util;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class Sha256Utils {

    private Sha256Utils() {
    }

    public static String hashUtf8(String value) {
        Objects.requireNonNull(value, "value");
        return hashBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String hashBytes(byte[] value) {
        Objects.requireNonNull(value, "value");
        return HexFormat.of().formatHex(newDigest().digest(value));
    }

    public static String hash(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        MessageDigest digest = newDigest();
        input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String fingerprintStrings(
            String nullRepresentation, int delimiter, String... values) {
        Objects.requireNonNull(nullRepresentation, "nullRepresentation");
        Objects.requireNonNull(values, "values");
        MessageDigest digest = newDigest();
        for (String value : values) {
            digest.update(
                    (value == null ? nullRepresentation : value).getBytes(StandardCharsets.UTF_8));
            digest.update(delimiterByte(delimiter));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String fingerprintObjects(int delimiter, Object... values) {
        Objects.requireNonNull(values, "values");
        MessageDigest digest = newDigest();
        // v2 用长度和独立空值标记编码；历史指纹不做模糊回退，避免重放其他参数的结果。
        try (DataOutputStream output = new DataOutputStream(
                new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            output.writeByte(2);
            output.writeByte(delimiterByte(delimiter));
            output.writeInt(values.length);
            for (Object value : values) {
                if (value == null) {
                    output.writeInt(-1);
                } else {
                    byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                    output.writeInt(bytes.length);
                    output.write(bytes);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode fingerprint", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte delimiterByte(int delimiter) {
        if (delimiter < Byte.MIN_VALUE || delimiter > 0xff) {
            throw new IllegalArgumentException("delimiter must fit in one byte");
        }
        return (byte) delimiter;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
