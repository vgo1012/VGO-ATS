package com.ezaigc.hr.common.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.pagination.OpaqueCursor;
import org.junit.jupiter.api.Test;
import com.ezaigc.hr.common.web.error.PlatformException;

class RequestValueObjectsTest {

    @Test
    void acceptsStableIdempotencyAndCursorTokens() {
        assertThat(IdempotencyKey.fromHttpHeader("screening:01JABC.retry-2"))
                .isEqualTo("screening:01JABC.retry-2");
        assertThat(OpaqueCursor.requireValid("eyJzb3J0IjoiTkFNRSJ9"))
                .isEqualTo("eyJzb3J0IjoiTkFNRSJ9");
    }

    @Test
    void rejectsHeaderAndCursorInjectionCharacters() {
        assertThatThrownBy(() -> IdempotencyKey.fromHttpHeader("unsafe key\r\n"))
                .isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> OpaqueCursor.requireValid("not/base64"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsInvalidIdempotencyHeaderToTheSharedHttpContract() {
        assertThatThrownBy(() -> IdempotencyKey.fromHttpHeader("unsafe key"))
                .isInstanceOfSatisfying(
                        PlatformException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
                            assertThat(exception.recoverable()).isTrue();
                        });
    }
}
