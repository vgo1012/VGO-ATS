package com.ezaigc.hr.integration.storage;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "hr.object-storage.mode",
    havingValue = "local",
    matchIfMissing = true)
public class LocalObjectStorageAdapter implements ObjectStoragePort {
  @Resource private IntegrationProperties properties;
  /** 本地存储根目录。 */
  private Path root;

  @PostConstruct
  void initializeStorageRoot() {
    root = Path.of(properties.getObjectStorage().getLocalRoot()).toAbsolutePath().normalize();
  }

  @Override
  public void put(String objectKey, InputStream content) throws IOException {
    Path target = resolve(objectKey);
    Files.createDirectories(target.getParent());
    Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public InputStream open(String objectKey) throws IOException {
    return Files.newInputStream(resolve(objectKey));
  }

  @Override
  public boolean exists(String objectKey) {
    return Files.isRegularFile(resolve(objectKey));
  }

  /** 删除指定业务记录。 */
  @Override
  public void delete(String objectKey) throws IOException {
    Files.deleteIfExists(resolve(objectKey));
  }

  private Path resolve(String objectKey) {
    Path target = root.resolve(objectKey).normalize();
    if (!target.startsWith(root))
      throw new IllegalArgumentException("object key escapes storage root");
    return target;
  }
}
