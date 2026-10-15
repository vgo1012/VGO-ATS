package com.ezaigc.hr.common.core.id;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;

public final class UlidGenerator implements PlatformIdGenerator {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    /**
     * ULID 编码长度。
     */
    private static final int ULID_LENGTH = 26;
    /**
     * 时钟。
     */
    private final Clock clock;
    /**
     * 安全随机数生成器。
     */
    private final SecureRandom random;

    public UlidGenerator(Clock clock) {
        this(clock, new SecureRandom());
    }

    UlidGenerator(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String newId() {
        long timestamp = clock.millis();
        if (timestamp < 0 || timestamp > 0xFFFF_FFFF_FFFFL) {
            throw new IllegalStateException("Clock is outside the ULID timestamp range");
        }

        byte[] value = new byte[16];
        value[0] = (byte) (timestamp >>> 40);
        value[1] = (byte) (timestamp >>> 32);
        value[2] = (byte) (timestamp >>> 24);
        value[3] = (byte) (timestamp >>> 16);
        value[4] = (byte) (timestamp >>> 8);
        value[5] = (byte) timestamp;
        byte[] entropy = new byte[10];
        random.nextBytes(entropy);
        System.arraycopy(entropy, 0, value, 6, entropy.length);

        BigInteger encoded = new BigInteger(1, value);
        char[] result = new char[ULID_LENGTH];
        for (int index = ULID_LENGTH - 1; index >= 0; index--) {
            result[index] = CROCKFORD[encoded.and(BigInteger.valueOf(31)).intValue()];
            encoded = encoded.shiftRight(5);
        }
        return new String(result);
    }
}
