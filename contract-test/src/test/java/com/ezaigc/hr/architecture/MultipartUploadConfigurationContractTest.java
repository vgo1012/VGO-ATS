package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class MultipartUploadConfigurationContractTest {

  @Test
  void servletMultipartLimitsSupportTwoMonthlyInputsAndKeepSingleFileBounded() throws Exception {
    Path application =
        Path.of("..", "bootstrap", "src", "main", "resources", "application.yml")
            .toAbsolutePath()
            .normalize();
    List<PropertySource<?>> documents =
        new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(application));

    assertEquals(
        "10MB", documents.getFirst().getProperty("spring.servlet.multipart.max-file-size"));
    assertEquals(
        "21MB", documents.getFirst().getProperty("spring.servlet.multipart.max-request-size"));
  }
}
