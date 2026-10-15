package com.ezaigc.hr.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.integration.storage.ClamAvMalwareScanAdapter;
import com.ezaigc.hr.integration.storage.DevelopmentMalwareScanAdapter;
import com.ezaigc.hr.integration.storage.LocalObjectStorageAdapter;
import com.ezaigc.hr.integration.storage.MinioObjectStorageAdapter;
import com.ezaigc.hr.integration.storage.AliyunOssObjectStorageAdapter;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;

class IntegrationAdapterConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(AdapterConfiguration.class)
          .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));

  @Test
  void selectsMinioStorageAndDevelopmentScannerByExplicitMode() {
    contextRunner
        .withPropertyValues(
            "hr.object-storage.mode=minio",
            "hr.object-storage.endpoint=http://192.0.2.142:9000",
            "hr.object-storage.access-key=test-access-key",
            "hr.object-storage.secret-key=test-secret-key",
            "hr.object-storage.bucket=test-bucket",
            "hr.malware-scan.mode=development")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObjectStoragePort.class);
              assertThat(context.getBean(ObjectStoragePort.class))
                  .isInstanceOf(MinioObjectStorageAdapter.class);
              assertThat(context).hasSingleBean(MalwareScanPort.class);
              assertThat(context.getBean(MalwareScanPort.class))
                  .isInstanceOf(DevelopmentMalwareScanAdapter.class);
            });
  }

  @Test
  void keepsOfflineStorageAndClamAvAsExplicitAlternativeModes() {
    contextRunner
        .withPropertyValues(
            "hr.object-storage.mode=local",
            "hr.object-storage.local-root=.runtime/test-objects",
            "hr.malware-scan.mode=clamav",
            "hr.malware-scan.host=192.0.2.142")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObjectStoragePort.class);
              assertThat(context.getBean(ObjectStoragePort.class))
                  .isInstanceOf(LocalObjectStorageAdapter.class);
              assertThat(context).hasSingleBean(MalwareScanPort.class);
              assertThat(context.getBean(MalwareScanPort.class))
                  .isInstanceOf(ClamAvMalwareScanAdapter.class);
            });
  }

  @Test
  void selectsAliyunOssWithEcsRamRoleWithoutStaticCloudCredentials() {
    contextRunner
        .withPropertyValues(
            "hr.object-storage.mode=oss",
            "hr.object-storage.oss.endpoint=https://oss-cn-hangzhou-internal.aliyuncs.com",
            "hr.object-storage.oss.region=cn-hangzhou",
            "hr.object-storage.oss.bucket=vgo-example-private",
            "hr.object-storage.oss.role-name=vgo-example-oss-role",
            "hr.malware-scan.mode=development")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObjectStoragePort.class);
              assertThat(context.getBean(ObjectStoragePort.class))
                  .isInstanceOf(AliyunOssObjectStorageAdapter.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IntegrationProperties.class)
  @Import({
    MinioObjectStorageAdapter.class,
    AliyunOssObjectStorageAdapter.class,
    LocalObjectStorageAdapter.class,
    ClamAvMalwareScanAdapter.class,
    DevelopmentMalwareScanAdapter.class
  })
  static class AdapterConfiguration {}
}
