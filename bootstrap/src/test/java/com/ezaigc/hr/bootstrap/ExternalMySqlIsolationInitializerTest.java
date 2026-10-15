package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class ExternalMySqlIsolationInitializerTest {
  @ParameterizedTest
  @ValueSource(strings = {"jdbc:mysql://localhost/hr_ai", "jdbc:mysql://localhost/hr_platform_it_short",
      "jdbc:mysql://user:secret@localhost/hr_platform_it_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "jdbc:mysql://localhost/", "jdbc:mysql://localhost", "jdbc:postgresql://localhost/hr_ai"})
  void rejectsUnsafeDatabaseTargetsBeforeAnyConnection(String url) {
    assertThatThrownBy(() -> ExternalMySqlIsolationInitializer.requireIsolatedSchema(url))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("generated hr_platform_it")
        .hasMessageNotContaining("secret");
  }

  @Test
  void acceptsOnlyTheGeneratedDatabaseName() {
    String schema = "hr_platform_it_" + "a".repeat(32);
    assertThat(ExternalMySqlIsolationInitializer.requireIsolatedSchema("jdbc:mysql://localhost/" + schema + "?useSSL=false"))
        .isEqualTo(schema);
  }

  @Test
  void rejectsAnExistingObjectDirectoryBeforeDatabaseConnection(@TempDir Path temporary) throws Exception {
    String schema = "hr_platform_it_" + "a".repeat(32);
    // 夹具使用真实路径，避免 macOS /var 别名先触发符号链接校验。
    Path objects = Files.createDirectories(temporary.resolve(schema).resolve("objects")).toRealPath();
    Files.writeString(objects.resolve("existing.txt"), "existing test data");
    try (var context = new GenericApplicationContext()) {
      context.setEnvironment(new MockEnvironment()
          .withProperty("spring.datasource.url", "jdbc:mysql://localhost/" + schema)
          .withProperty("hr.object-storage.local-root", objects.toString()));
      assertThatThrownBy(() -> new ExternalMySqlIsolationInitializer().initialize(context))
          .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be empty");
    }
  }
}
