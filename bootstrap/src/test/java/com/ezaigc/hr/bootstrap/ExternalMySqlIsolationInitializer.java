package com.ezaigc.hr.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** 在数据源、Flyway及管理员初始化之前拒绝共享库和共享对象目录。 */
class ExternalMySqlIsolationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext context) {
    var environment = context.getEnvironment();
    if (Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> profile.equals("dev") || profile.equals("prod"))) {
      throw new IllegalStateException("External integration tests cannot use dev/prod profiles");
    }
    String url = environment.getRequiredProperty("spring.datasource.url");
    String schema = requireIsolatedSchema(url);
    Path objects = Path.of(environment.getRequiredProperty("hr.object-storage.local-root")).toAbsolutePath().normalize();
    try {
      if (!Files.isDirectory(objects) || !objects.equals(objects.toRealPath())
          || objects.getParent() == null || !objects.getParent().getFileName().toString().equals(schema)) {
        throw new IllegalStateException("External tests require an isolated object directory owned by the test schema");
      }
      try (var contents = Files.list(objects)) {
        if (contents.findAny().isPresent()) throw new IllegalStateException("External test object directory must be empty");
      }
      try (var connection = DriverManager.getConnection(url,
              environment.getRequiredProperty("spring.datasource.username"),
              environment.getRequiredProperty("spring.datasource.password"));
          var statement = connection.createStatement();
          var result = statement.executeQuery("SELECT DATABASE(),(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE())")) {
        if (!result.next() || !schema.equals(result.getString(1)) || result.getInt(2) != 0) {
          throw new IllegalStateException("External integration tests require a new empty isolated database");
        }
      }
    } catch (IOException | SQLException exception) {
      throw new IllegalStateException("Cannot verify external test isolation", exception);
    }
  }

  static String requireIsolatedSchema(String url) {
    try {
      if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
      URI endpoint = URI.create(url.substring(5));
      String schema = endpoint.getPath().substring(1);
      if (endpoint.getHost() == null || endpoint.getRawUserInfo() != null || endpoint.getFragment() != null
          || !schema.matches("hr_platform_it_[0-9a-f]{32}")) throw new IllegalArgumentException();
      return schema;
    } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
      throw new IllegalStateException("External integration tests require a generated hr_platform_it database");
    }
  }
}
