package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ActiveProfiles;

class PlatformExternalConfigurationTest {
  @Test
  void usesExplicitIsolatedDatabaseAndLocalExternalResources() {
    String database = "jdbc:mysql://127.0.0.1:3306/hr_platform_it_" + "a".repeat(32);
    try (var context = new GenericApplicationContext()) {
      Path root = Path.of("").toAbsolutePath();
      while (root != null && !Files.isRegularFile(root.resolve("bootstrap/src/main/resources/application.yml"))) {
        root = root.getParent();
      }
      assertThat(root).isNotNull();
      TestPropertyValues.of(
          "spring.config.location=" + root.resolve("bootstrap/src/main/resources/application.yml").toUri(),
          // 只解析配置，不刷新上下文或建立连接；缺少本机文件不阻挡检查测试绑定。
          "spring.config.import=",
          "spring.config.on-not-found=ignore",
          "HR_DB_URL=" + database,
          "HR_DB_USER=isolated-user",
          "HR_DB_PASSWORD=isolated-test-only",
          "HR_EXTERNAL_OBJECT_ROOT=" + root.resolve("target/isolated-objects").toString())
          .applyTo(context);
      var profiles = PlatformExternalMySqlIT.class.getAnnotation(ActiveProfiles.class);
      if (profiles != null) context.getEnvironment().setActiveProfiles(profiles.value());
      TestPropertyValues.of(PlatformExternalMySqlIT.class.getAnnotation(SpringBootTest.class).properties())
          .applyTo(context);
      new ConfigDataApplicationContextInitializer().initialize(context);

      var environment = context.getEnvironment();
      assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(database);
      assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("isolated-user");
      assertThat(environment.getProperty("hr.object-storage.mode")).isEqualTo("local");
      assertThat(environment.getProperty("hr.object-storage.local-root"))
          .isEqualTo(root.resolve("target/isolated-objects").toString());
      assertThat(environment.getProperty("hr.search.mode")).isEqualTo("memory");
      assertThat(environment.getProperty("hr.malware-scan.mode")).isEqualTo("development");
      assertThat(environment.getActiveProfiles()).doesNotContain("dev", "prod");
    }
  }
}
