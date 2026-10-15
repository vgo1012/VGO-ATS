package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class DocumentExtractionConfigurationContractTest {

  @Test
  void commonRuntimeConfigurationDeclaresBoundedDocumentExtractionDefaults() throws Exception {
    Path application =
        Path.of("..", "bootstrap", "src", "main", "resources", "application.yml")
            .toAbsolutePath()
            .normalize();
    List<PropertySource<?>> documents =
        new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(application));
    PropertySource<?> common = documents.getFirst();

    assertEquals(100, common.getProperty("hr.document-extraction.maximum-pages"));
    assertEquals(
        500_000, common.getProperty("hr.document-extraction.maximum-extracted-characters"));
    assertEquals(
        67_108_864,
        common.getProperty("hr.document-extraction.maximum-container-expanded-bytes"));
    assertEquals(4_096, common.getProperty("hr.document-extraction.maximum-container-entries"));
    assertEquals(65_536, common.getProperty("hr.document-extraction.maximum-pdf-resource-nodes"));
    assertEquals(
        268_435_456, common.getProperty("hr.document-extraction.maximum-pdf-expanded-bytes"));
    assertEquals(
        32_000_000, common.getProperty("hr.document-extraction.maximum-source-image-pixels"));
    assertEquals(
        20_000, common.getProperty("hr.document-extraction.maximum-source-image-dimension"));
    assertEquals(150, common.getProperty("hr.document-extraction.render-dpi"));
    assertEquals(
        8_000_000,
        common.getProperty("hr.document-extraction.maximum-rendered-page-pixels"));
    assertEquals(
        10_000,
        common.getProperty("hr.document-extraction.maximum-rendered-page-dimension"));
    assertEquals(40, common.getProperty("hr.document-extraction.minimum-page-characters"));
    assertEquals(0.80, common.getProperty("hr.document-extraction.minimum-printable-ratio"));
  }
}
