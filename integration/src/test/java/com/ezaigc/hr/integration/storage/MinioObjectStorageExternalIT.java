package com.ezaigc.hr.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

/** 仅使用显式配置的测试服务，既有桶内只访问本次新建的随机对象键。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MINIO_IT", matches = "true")
class MinioObjectStorageExternalIT {
  @Test
  void putReadStatAndRepeatedDeleteUseTheConfiguredMinioService() {
    var properties = new IntegrationProperties();
    var storage = properties.getObjectStorage();
    storage.setMode("minio");
    storage.setEndpoint(required("HR_MINIO_ENDPOINT"));
    storage.setBucket(required("HR_MINIO_BUCKET"));
    storage.setAccessKey(required("HR_MINIO_ACCESS_KEY"));
    storage.setSecretKey(required("HR_MINIO_SECRET_KEY"));
    var adapter = new MinioObjectStorageAdapter();
    ReflectionTestUtils.setField(adapter, "properties", properties);
    String key = "external-it/" + UUID.randomUUID() + "/synthetic.txt";
    byte[] expected = "Synthetic object storage verification\n".repeat(100).getBytes(StandardCharsets.UTF_8);
    boolean owned = false;
    try {
      adapter.initializeClient();
      try {
        assertThat(adapter.exists(key)).as("本次随机对象键必须尚不存在").isFalse();
        owned = true;
        for (int retry = 0; retry < 2; retry++) {
          try (var content = new ByteArrayInputStream(expected)) {
            adapter.put(key, content);
          }
          assertThat(adapter.exists(key)).isTrue();
          try (var content = adapter.open(key)) {
            assertThat(content.readAllBytes()).isEqualTo(expected);
          }
        }
        adapter.delete(key);
        adapter.delete(key);
        assertThat(adapter.exists(key)).isFalse();
      } finally {
        if (owned) {
          adapter.delete(key);
          assertThat(adapter.exists(key)).as("本次合成对象必须已清理").isFalse();
        }
      }
    } catch (Exception failure) {
      // 第三方S3异常可能携带签名请求头；失败报告只保留类型及标准错误码。
      throw safeFailure(failure);
    } finally {
      try {
        adapter.closeClient();
      } catch (Exception failure) {
        throw safeFailure(failure);
      }
    }
  }

  private static AssertionError safeFailure(Exception failure) {
    String code = failure.getClass().getSimpleName();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ErrorResponseException response) {
        code = response.errorResponse().code();
        break;
      }
    }
    return new AssertionError("MinIO合成对象验证失败，已屏蔽第三方请求信息；错误类型/码：" + code);
  }

  private static String required(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置 %s", name).isNotBlank();
    return value;
  }
}
