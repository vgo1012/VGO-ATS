package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.integration.ai.VersionedJsonSchemaOutputConverter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/** Binds the runtime's versioned AI schema resources to unambiguous injectable types. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hr.ai.parsing.enabled", havingValue = "true")
public class AiSchemaConfiguration {

  @Bean
  VersionedJsonSchemaOutputConverter resumeParseJsonSchema() {
    return new VersionedJsonSchemaOutputConverter(
        "1.2", resource("schema/resume-parse/1.2.json"));
  }

  @Bean
  VersionedJsonSchemaOutputConverter jdParseJsonSchema() {
    return new VersionedJsonSchemaOutputConverter(
        "1.1", resource("schema/jd-parse/1.1.json"));
  }

  private static String resource(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("missing AI schema: " + path, ex);
    }
  }
}
