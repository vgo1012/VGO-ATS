package com.ezaigc.hr.common.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PlatformExceptionTest {

  @Test
  void supportsPreservingAnInfrastructureFailureCause() {
    assertThat(Arrays.stream(PlatformException.class.getConstructors()))
        .map(Constructor::getParameterTypes)
        .anyMatch(types -> Arrays.asList(types).contains(Throwable.class));

    IllegalStateException cause = new IllegalStateException("dependency failed");
    PlatformException exception =
        PlatformExceptions.unavailable("DEPENDENCY_UNAVAILABLE", "依赖服务暂时不可用", cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }
}
