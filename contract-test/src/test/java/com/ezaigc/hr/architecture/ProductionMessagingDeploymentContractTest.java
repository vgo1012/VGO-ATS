package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

class ProductionMessagingDeploymentContractTest {

  @Test
  void productionProfileAndComposeEnableDurableRabbitTaskNotifications() throws IOException {
    Path root = repositoryRoot();
    Path production = root.resolve("bootstrap/src/main/resources/application-prod.yml");
    var properties =
        new YamlPropertySourceLoader()
            .load("application-prod.yml", new FileSystemResource(production))
            .getFirst();

    assertEquals("rabbitmq", properties.getProperty("hr.messaging.mode"));
    assertEquals(true, properties.getProperty("hr.messaging.worker-enabled"));
    assertEquals(true, properties.getProperty("hr.messaging.consumer-enabled"));
    assertEquals("rabbitmq", properties.getProperty("hr.messaging.host"));

    String compose =
        Files.readString(root.resolve("deploy/docker-compose.prod.yml")).replace("\r\n", "\n");
    assertTrue(compose.contains("rabbitmq:4.3.6-management@sha256:"));
    assertTrue(compose.contains("rabbitmq-diagnostics\", \"-q\", \"ping"));
    assertTrue(compose.contains("rabbitmq_data:/var/lib/rabbitmq"));
    assertTrue(compose.contains("HR_MESSAGING_USERNAME: ${HR_RABBITMQ_USERNAME:"));
    assertTrue(compose.contains("HR_MESSAGING_PASSWORD: ${HR_RABBITMQ_PASSWORD:"));
    assertTrue(compose.contains("rabbitmq:\n        condition: service_healthy"));

    String environment = Files.readString(root.resolve("deploy/.env.example"));
    assertTrue(environment.contains("HR_RABBITMQ_USERNAME=hr_app"));
    assertTrue(environment.contains("HR_RABBITMQ_PASSWORD=REPLACE_WITH_RABBITMQ_PASSWORD"));
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
