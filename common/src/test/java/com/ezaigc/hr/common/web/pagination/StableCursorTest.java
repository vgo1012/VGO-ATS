package com.ezaigc.hr.common.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StableCursorTest {

    private static final CursorCodec CODEC =
            new SignedCursorCodec("stable-cursor-test-signing-key-32-bytes");
    private static final String SCOPE = "test.stable-cursor";

    @Test
    void roundTripsStableTimeAndId() {
        var cursor = new StableCursor(Instant.parse("2026-09-01T12:34:56.123456Z"), "01K45Q9W3ZK1BM8B4K7B49K83A");
        assertThat(StableCursor.decode(CODEC, SCOPE, cursor.encode(CODEC, SCOPE))).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedPayload() {
        assertThatThrownBy(() -> StableCursor.decode(CODEC, SCOPE, "YWJj"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
    }

    @Test
    void rejectsCursorFromAnotherQueryScope() {
        var cursor = new StableCursor(Instant.parse("2026-09-01T12:34:56Z"), "01K45Q9W3ZK1BM8B4K7B49K83A");
        String token = cursor.encode(CODEC, SCOPE);

        assertThatThrownBy(() -> StableCursor.decode(CODEC, "test.another-query", token))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
    }

    @Test
    void rejectsTamperedSignedCursor() {
        var cursor = new StableCursor(Instant.parse("2026-09-01T12:34:56Z"), "01K45Q9W3ZK1BM8B4K7B49K83A");
        String token = cursor.encode(CODEC, SCOPE);
        char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> StableCursor.decode(CODEC, SCOPE, tampered))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
    }
}
