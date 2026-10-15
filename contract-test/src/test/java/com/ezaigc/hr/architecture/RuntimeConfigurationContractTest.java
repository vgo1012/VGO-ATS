package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.ObjectMapper;

class RuntimeConfigurationContractTest {

  @Test
  void composeEnvironmentDisablesLifecycleEvenIfAnOlderConfigurationEnabledIt() {
    var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("old-configuration",
        Map.of("hr.resume-lifecycle.enabled", true)));
    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("local-compose-systemEnvironment",
        Map.of("HR_RESUMELIFECYCLE_ENABLED", "false")));
    var properties = Binder.get(environment)
        .bind("hr.resume-lifecycle", ResumeLifecycleProperties.class).get();
    assertFalse(properties.isEnabled());
  }

  @Test
  void trackedRuntimeConfigurationSeparatesCommonAndDevelopmentSettings()
      throws IOException {
    Path resources = repositoryRoot().resolve("bootstrap/src/main/resources");
    Path application = resources.resolve("application.yml");
    Path development = resources.resolve("application-dev.yml");
    Path production = resources.resolve("application-prod.yml");
    String commonConfiguration = Files.readString(application);
    String developmentConfiguration = Files.readString(development);
    String productionConfiguration = Files.readString(production);
    var commonDocuments =
        new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(application));
    var developmentDocuments =
        new YamlPropertySourceLoader()
            .load("application-dev.yml", new FileSystemResource(development));
    var productionDocuments =
        new YamlPropertySourceLoader()
            .load("application-prod.yml", new FileSystemResource(production));

    assertEquals(
        1, commonDocuments.size(), "application.yml must contain only common configuration");
    assertEquals(
        1,
        developmentDocuments.size(),
        "application-dev.yml must contain only development configuration");
    assertEquals(
        1,
        productionDocuments.size(),
        "application-prod.yml must contain one production document");
    assertEquals("dev", commonDocuments.getFirst().getProperty("spring.profiles.default"));
    assertFalse(developmentDocuments.getFirst().containsProperty("spring.config.import"));
    assertEquals("none", commonDocuments.getFirst().getProperty("spring.ai.model.chat"));
    assertEquals(2, commonDocuments.getFirst().getProperty("hr.executors.search.core-size"),
        "search projection and rebuild require independent runnable slots");
    var developmentProperties = developmentDocuments.getFirst();
    assertEquals("127.0.0.1", developmentProperties.getProperty("server.address"));
    assertEquals(false, developmentProperties.getProperty("hr.ai.parsing.enabled"));
    assertEquals(false, developmentProperties.getProperty("hr.ai.multimodal.enabled"));
    assertFalse(developmentProperties.containsProperty("spring.ai.openai.api-key"));
    assertFalse(developmentProperties.containsProperty("spring.ai.model.chat"));
    assertEquals(
        "local-development-cursor-signing-key-change-me",
        developmentProperties.getProperty("hr.pagination.cursor-signing-key"),
        "the development-only signing value must not leak into common configuration");
    assertEquals(
        "jdbc:mysql://localhost:3306/vgo_hr?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        developmentProperties.getProperty("spring.datasource.url"));
    assertEquals("hr_app", developmentProperties.getProperty("spring.datasource.username"));
    assertEquals("local-development-db-password", developmentProperties.getProperty("spring.datasource.password"));
    assertEquals(5000, developmentProperties.getProperty("spring.datasource.hikari.connection-timeout"));
    assertEquals(120000, developmentProperties.getProperty("spring.datasource.hikari.keepalive-time"));
    assertEquals(300000, developmentProperties.getProperty("spring.datasource.hikari.idle-timeout"));
    assertEquals(600000, developmentProperties.getProperty("spring.datasource.hikari.max-lifetime"));
    assertEquals("readinessState,db", commonDocuments.getFirst().getProperty(
        "management.endpoint.health.group.readiness.include"));
    assertEquals("local", developmentProperties.getProperty("hr.object-storage.mode"));
    assertEquals(".runtime/objects", developmentProperties.getProperty("hr.object-storage.local-root"));
    assertEquals("elasticsearch", developmentProperties.getProperty("hr.search.mode"));
    assertEquals("http://localhost:9200", developmentProperties.getProperty("hr.search.elasticsearch.base-url"));
    assertEquals("elastic", developmentProperties.getProperty("hr.search.elasticsearch.username"));
    assertEquals("local-development-search-password", developmentProperties.getProperty("hr.search.elasticsearch.password"));
    assertEquals(0, developmentProperties.getProperty("hr.search.elasticsearch.number-of-replicas"));
    assertFalse(commonDocuments.getFirst().containsProperty("hr.pagination.cursor-signing-key"));
    assertFalse(developmentConfiguration.contains(".runtime/config/"));
    assertFalse(developmentConfiguration.contains("${"), "local startup must not require external credentials");
    assertEquals(false, commonDocuments.getFirst().getProperty("hr.mail-resume.enabled"));
    assertEquals(false, commonDocuments.getFirst().getProperty("hr.wecom.directory-enabled"));
    assertEquals(false, commonDocuments.getFirst().getProperty("hr.wecom.collaboration.enabled"));
    assertEquals(false, commonDocuments.getFirst().getProperty("hr.wecom.sync.enabled"));
    assertEquals(false, commonDocuments.getFirst().getProperty("hr.resume-lifecycle.enabled"));
    assertFalse(developmentProperties.containsProperty("hr.resume-lifecycle.enabled"));
    assertFalse(productionDocuments.getFirst().containsProperty("hr.resume-lifecycle.enabled"));
    assertEquals("disabled", commonDocuments.getFirst().getProperty("hr.messaging.mode"));
    assertEquals(
        "file:/run/secrets/application-prod-secrets.yml",
        productionDocuments.getFirst().getProperty("spring.config.import"));
    assertEquals(
        true,
        productionDocuments.getFirst().getProperty("server.servlet.session.cookie.secure"));
    assertEquals(false, productionDocuments.getFirst().getProperty("springdoc.api-docs.enabled"));
    assertEquals("clamav", productionDocuments.getFirst().getProperty("hr.malware-scan.mode"));
    assertEquals("elasticsearch", productionDocuments.getFirst().getProperty("hr.search.mode"));
    assertFalse(productionDocuments.getFirst().containsProperty("spring.ai.openai.base-url"));
    assertEquals(false, productionDocuments.getFirst().getProperty("hr.ai.parsing.enabled"));
    assertEquals(false, productionDocuments.getFirst().getProperty("hr.ai.multimodal.enabled"));
    assertFalse(productionDocuments.getFirst().containsProperty("hr.executors.ai.core-size"));
    assertFalse(productionConfiguration.contains("password:"));
    assertFalse(productionConfiguration.contains("access-key:"));
    assertFalse(productionConfiguration.contains("api-key:"));


  }

  @Test
  void localComposeMatchesDisposableDevelopmentServicesAndUsesIsolatedVolumes() throws IOException {
    Path compose = repositoryRoot().resolve("compose.yaml");
    var properties = new YamlPropertySourceLoader()
        .load("local-compose", new FileSystemResource(compose)).getFirst();
    assertEquals("vgo-local", properties.getProperty("name"));
    assertEquals("vgo_hr", properties.getProperty("services.mysql.environment.MYSQL_DATABASE"));
    assertEquals("hr_app", properties.getProperty("services.mysql.environment.MYSQL_USER"));
    assertEquals("local-development-db-password", properties.getProperty("services.mysql.environment.MYSQL_PASSWORD"));
    assertFalse(properties.containsProperty("services.mysql.ports[0]"));
    assertEquals("local-development-search-password", properties.getProperty("services.elasticsearch.environment.ELASTIC_PASSWORD"));
    assertFalse(properties.containsProperty("services.elasticsearch.ports[0]"));
    assertFalse(properties.containsProperty("services.backend.ports[0]"));
    assertEquals("127.0.0.1:5173:80", properties.getProperty("services.web.ports[0]"));
    assertEquals("dev,docker", properties.getProperty("services.backend.environment.SPRING_PROFILES_ACTIVE"));
    assertEquals("false", properties.getProperty("services.backend.environment.HR_RESUMELIFECYCLE_ENABLED"));
    assertEquals("Asia/Shanghai", properties.getProperty("services.backend.environment.TZ"));
    assertEquals("service_healthy", properties.getProperty("services.backend.depends_on.mysql.condition"));
    assertEquals("service_healthy", properties.getProperty("services.backend.depends_on.elasticsearch.condition"));
    assertEquals("service_healthy", properties.getProperty("services.web.depends_on.backend.condition"));
    assertEquals("local", properties.getProperty("services.web.build.target"));
    assertEquals("object_data:/app/.runtime", properties.getProperty("services.backend.volumes[0]"));
    assertEquals("backend_logs:/app/logs", properties.getProperty("services.backend.volumes[1]"));
    assertFalse(properties.containsProperty("volumes.mysql_data.external"));
    assertFalse(properties.containsProperty("volumes.elasticsearch_data.external"));
    assertFalse(properties.containsProperty("volumes.mysql_data.name"));
    assertFalse(properties.containsProperty("volumes.elasticsearch_data.name"));
    for (String volume : Set.of("mysql_data", "elasticsearch_data", "object_data", "backend_logs")) {
      assertFalse(properties.containsProperty("volumes." + volume + ".external"));
      assertFalse(properties.containsProperty("volumes." + volume + ".name"));
    }
    var docker = new YamlPropertySourceLoader().load("docker", new FileSystemResource(
        repositoryRoot().resolve("bootstrap/src/main/resources/application-docker.yml"))).getFirst();
    assertEquals("0.0.0.0", docker.getProperty("server.address"));
    assertTrue(docker.getProperty("spring.datasource.url").toString().startsWith("jdbc:mysql://mysql:3306/vgo_hr?"));
    assertEquals("http://elasticsearch:9200", docker.getProperty("hr.search.elasticsearch.base-url"));
    assertFalse(docker.containsProperty("hr.resume-lifecycle.enabled"));
  }

  @Test
  void bootstrapUsesSpringBootJdbcSessionStarter() throws IOException {
    String bootstrapPom = Files.readString(repositoryRoot().resolve("bootstrap/pom.xml"));

    assertTrue(
        bootstrapPom.contains("<artifactId>spring-boot-starter-session-jdbc</artifactId>"),
        "Spring Boot 4 requires its JDBC session starter to activate JDBC session auto-configuration");
    assertFalse(
        bootstrapPom.contains("<artifactId>spring-session-jdbc</artifactId>"),
        "the library alone does not install Spring Boot 4 JDBC session auto-configuration");
  }

  @Test
  void intentionalCompositeKeyMetadataWarningsAreQuieted() throws IOException {
    String application =
        Files.readString(repositoryRoot().resolve("bootstrap/src/main/resources/application.yml"));

    assertTrue(
        application.contains(
            "com.baomidou.mybatisplus.core.metadata.TableInfoHelper: error"),
        "intentional composite-key entities must not flood startup logs with metadata warnings");
    assertTrue(
        application.contains(
            "com.baomidou.mybatisplus.core.injector.DefaultSqlInjector: error"),
        "unsupported generated ById methods must not flood startup logs with injector warnings");
  }

  @Test
  void vscodeDevelopmentLaunchBypassesProxyForConfiguredServices() throws IOException {
    Path root = repositoryRoot();
    var launch = new ObjectMapper().readTree(Files.readString(root.resolve(".vscode/launch.json")))
        .path("configurations").get(0);
    assertEquals("java", launch.path("type").asText());
    assertEquals("launch", launch.path("request").asText());
    assertEquals("com.ezaigc.hr.bootstrap.HrPlatformApplication", launch.path("mainClass").asText());
    assertEquals("${workspaceFolder}", launch.path("cwd").asText());
    assertEquals("--spring.profiles.active=dev", launch.path("args").get(0).asText());

    String bypass = "localhost|127.*|[::1]";
    var arguments = new HashSet<String>();
    for (var argument : launch.path("vmArgs")) arguments.add(argument.asText());
    String readme = Files.readString(root.resolve("README.md"));
    for (String property : Set.of("socksNonProxyHosts", "http.nonProxyHosts")) {
      String argument = "-D" + property + "=" + bypass;
      assertTrue(arguments.contains(argument), "VS Code must bypass proxies for local services");
      assertTrue(readme.contains(argument), "VS Code and documented startup must use the same bypass");
    }
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("contract-test"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }

}
