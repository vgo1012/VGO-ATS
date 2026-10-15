package com.ezaigc.hr.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.aliyun.oss.OSSException;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 仅在绑定 ECS RAM 角色的主机运行，既有 Bucket 内只访问本次新建的随机对象键。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_OSS_IT", matches = "true")
class AliyunOssObjectStorageExternalIT {

  @Test
  void verifiesEcsRolePutReadStatAndRepeatedDeleteAgainstOss() {
    IntegrationProperties properties = new IntegrationProperties();
    IntegrationProperties.ObjectStorage storage = properties.getObjectStorage();
    storage.setMode("oss");
    storage.setOss(new IntegrationProperties.Oss(
        "ecs-ram-role",
        required("HR_OSS_ENDPOINT"),
        required("HR_OSS_REGION"),
        required("HR_OSS_BUCKET"),
        environmentOrDefault("HR_OSS_ROLE_NAME", "vgo-example-oss-role")));
    AliyunOssObjectStorageAdapter adapter = new AliyunOssObjectStorageAdapter();
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("oss-external-it-deadline-");
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.initialize();
    ReflectionTestUtils.setField(adapter, "taskScheduler", scheduler);
    String key = "external-it/" + UUID.randomUUID() + "/synthetic.txt";
    byte[] expected = "Synthetic OSS verification\n".repeat(100)
        .getBytes(StandardCharsets.UTF_8);
    boolean owned = false;
    try {
      adapter.initializeClient();
      assertThat(adapter.exists(key)).as("本次随机对象键必须尚不存在").isFalse();
      owned = true;
      try (var content = new ByteArrayInputStream(expected)) {
        adapter.put(key, content);
      }
      assertThat(adapter.exists(key)).isTrue();
      try (var content = adapter.open(key)) {
        assertThat(content.readAllBytes()).isEqualTo(expected);
      }
      assertThat(adapter.snapshot().isAvailable()).as("角色必须具备 Bucket 统计权限").isTrue();
      adapter.delete(key);
      adapter.delete(key);
      assertThat(adapter.exists(key)).isFalse();
    } catch (Exception failure) {
      throw safeFailure(failure);
    } finally {
      try {
        try {
          if (owned) {
            adapter.delete(key);
            assertThat(adapter.exists(key)).as("本次合成对象必须已清理").isFalse();
          }
        } finally {
          adapter.closeClient();
        }
      } catch (Exception failure) {
        throw safeFailure(failure);
      } finally {
        scheduler.shutdown();
      }
    }
  }

  private static AssertionError safeFailure(Exception failure) {
    String code = failure.getClass().getSimpleName();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof OSSException response && response.getErrorCode() != null) {
        code = response.getErrorCode();
        break;
      }
    }
    return new AssertionError("OSS 合成对象验证失败，已屏蔽第三方请求信息；错误类型/码：" + code);
  }

  private static String required(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置 %s", name).isNotBlank();
    return value;
  }

  private static String environmentOrDefault(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
