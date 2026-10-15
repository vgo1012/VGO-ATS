package com.ezaigc.hr.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class FlywayResourcePackagingTest {
  private static final Pattern UPGRADE_NAME =
      Pattern.compile("hr_ai_upgrade_(\\d{8})_(\\d{2})(__[a-z][a-z0-9_]*\\.sql)");

  @Test
  void generatedMigrationsBelongToDeclaredMavenResources() throws Exception {
    Path root = repositoryRoot();
    Document pom = bootstrapPom(root);
    var directories = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/resources/resource/directory", pom, XPathConstants.NODESET);
    List<Path> resourceRoots = new ArrayList<>();
    for (int index = 0; index < directories.getLength(); index++) {
      resourceRoots.add(resolveMavenPath(root, directories.item(index).getTextContent()));
    }

    Set<Path> mappedSources = new HashSet<>();
    for (MigrationCopy copy : migrationCopies(root, pom)) {
      assertThat(copy.output().startsWith(root.resolve("bootstrap/target/classes")))
          .as("迁移生成不得直接写入会被 IDE Make 清理的 classes: %s", copy.output())
          .isFalse();
      assertThat(resourceRoots).as("生成目录必须是标准 Maven 资源根: %s", copy.output())
          .anyMatch(copy.output()::startsWith);
      var name = UPGRADE_NAME.matcher(copy.source().getFileName().toString());
      assertThat(name.matches()).isTrue();
      assertThat(copy.output().getFileName().toString())
          .isEqualTo("V" + name.group(1) + name.group(2) + name.group(3));
      assertThat(mappedSources.add(copy.source())).as("升级不得重复映射").isTrue();
    }
    try (var files = Files.list(root.resolve("docs/sql"))) {
      assertThat(mappedSources).containsExactlyInAnyOrderElementsOf(files
          .filter(path -> UPGRADE_NAME.matcher(path.getFileName().toString()).matches())
          .toList());
    }
    assertThat(mappedSources).isNotEmpty();
  }

  @Test
  void everyExplicitUpgradeReachesRuntimeClasspathWithoutChangingSql() throws Exception {
    Path root = repositoryRoot();
    List<MigrationCopy> copies = migrationCopies(root, bootstrapPom(root));
    assertThat(copies).isNotEmpty();
    for (MigrationCopy copy : copies) {
      assertThat(copy.output()).as("Maven generate-resources 应准备迁移").isRegularFile();
      byte[] source = Files.readAllBytes(copy.source());
      assertThat(Files.readAllBytes(copy.output())).isEqualTo(source);
      String resource = "db/migration/" + copy.output().getFileName();
      try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
        assertThat(input).as("标准 resources 复制必须使迁移在运行 classpath 可见: %s", resource)
            .isNotNull();
        assertThat(input.readAllBytes()).as(resource).isEqualTo(source);
      }
    }
    try (var baseline = getClass().getClassLoader()
        .getResourceAsStream("db/migration/V0001__complete_schema.sql")) {
      assertThat(baseline).isNotNull();
      assertThat(baseline.readAllBytes()).isEqualTo(Files.readAllBytes(root.resolve(
          "bootstrap/src/main/resources/db/migration/V0001__complete_schema.sql")));
    }
  }

  @Test
  void everyRuntimeYamlReachesClasspathWithoutChangingConfiguration() throws Exception {
    Path resources = repositoryRoot().resolve("bootstrap/src/main/resources");
    try (var files = Files.list(resources)) {
      for (Path source : files.filter(path -> path.getFileName().toString()
          .matches("application(?:-[a-z]+)?\\.yml")).toList()) {
        String resource = source.getFileName().toString();
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
          assertThat(input).as("启动配置必须在运行 classpath 可见: %s", resource).isNotNull();
          assertThat(input.readAllBytes()).as(resource).isEqualTo(Files.readAllBytes(source));
        }
      }
    }
  }

  @Test
  void sharedIdeLaunchCopiesResourcesAfterMake() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var run = factory.newDocumentBuilder().parse(repositoryRoot()
        .resolve(".run/HrPlatformApplication.run.xml").toFile());
    var tasks = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
        "/component/configuration/method/option", run, XPathConstants.NODESET);
    assertThat(tasks.getLength()).isEqualTo(2);
    var make = (Element) tasks.item(0);
    assertThat(make.getAttribute("name")).isEqualTo("Make");
    assertThat(make.getAttribute("enabled")).isEqualTo("true");
    var resources = (Element) tasks.item(1);
    assertThat(resources.getAttribute("name")).isEqualTo("Maven.BeforeRunTask");
    assertThat(resources.getAttribute("enabled")).isEqualTo("true");
    assertThat(resources.getAttribute("file")).isEqualTo("$PROJECT_DIR$/pom.xml");
    assertThat(resources.getAttribute("goal")).isEqualTo("-pl bootstrap -am process-resources");
  }

  @Test
  void applicationYamlExplicitlyStopsIgnoringFutureMigrations() {
    try (var context = new GenericApplicationContext()) {
      TestPropertyValues.of("spring.config.location=classpath:application.yml",
          "spring.config.import=", "spring.profiles.active=flyway-resource-test")
          .applyTo(context);
      new ConfigDataApplicationContextInitializer().initialize(context);
      FlywayProperties properties = Binder.get(context.getEnvironment())
          .bindOrCreate("spring.flyway", FlywayProperties.class);
      assertThat(properties.getIgnoreMigrationPatterns()).isNotNull().isEmpty();
    }
  }

  private static List<MigrationCopy> migrationCopies(Path root, Document pom) throws Exception {
    var copies = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/plugins/plugin[artifactId='maven-antrun-plugin']"
            + "/executions/execution/configuration/target/copy", pom, XPathConstants.NODESET);
    List<MigrationCopy> migrations = new ArrayList<>();
    for (int index = 0; index < copies.getLength(); index++) {
      var copy = (Element) copies.item(index);
      Path source = resolveMavenPath(root, copy.getAttribute("file"));
      if (UPGRADE_NAME.matcher(source.getFileName().toString()).matches()) {
        migrations.add(new MigrationCopy(source,
            resolveMavenPath(root, copy.getAttribute("tofile"))));
      }
    }
    return migrations;
  }

  private static Document bootstrapPom(Path root) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(root.resolve("bootstrap/pom.xml").toFile());
  }

  private static Path resolveMavenPath(Path root, String value) {
    String resolved = value.trim()
        .replace("${maven.multiModuleProjectDirectory}", root.toString())
        .replace("${project.basedir}", root.resolve("bootstrap").toString())
        .replace("${project.build.outputDirectory}", root.resolve("bootstrap/target/classes").toString())
        .replace("${project.build.directory}", root.resolve("bootstrap/target").toString());
    return root.resolve("bootstrap").resolve(resolved).normalize();
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate repository root");
  }

  private record MigrationCopy(Path source, Path output) {}
}
