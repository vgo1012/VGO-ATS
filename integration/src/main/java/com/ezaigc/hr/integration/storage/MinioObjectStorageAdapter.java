package com.ezaigc.hr.integration.storage;

import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import io.minio.GetObjectArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hr.object-storage.mode", havingValue = "minio")
public class MinioObjectStorageAdapter implements ObjectStoragePort {
  @Resource private IntegrationProperties properties;
  /** 客户。 */
  private MinioClient client;
  private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean closing = new AtomicBoolean();
  /** 对象存储桶名称。 */
  private String bucket;

  @PostConstruct
  void initializeClient() {
    IntegrationProperties.ObjectStorage storage = properties.getObjectStorage();
    String endpoint =
        IntegrationProperties.requireConfigured(
            storage.getEndpoint(), "hr.object-storage.endpoint");
    String accessKey =
        IntegrationProperties.requireConfigured(
            storage.getAccessKey(), "hr.object-storage.access-key");
    String secretKey =
        IntegrationProperties.requireConfigured(
            storage.getSecretKey(), "hr.object-storage.secret-key");
    bucket =
        IntegrationProperties.requireConfigured(storage.getBucket(), "hr.object-storage.bucket");
    if (!storage.hasValidTimeouts()) {
      throw new IllegalStateException("object storage timeouts must be positive and fit HTTP millisecond limits");
    }
    // 保留 SDK 的 HTTP/1.1、重试拦截器和外部证书配置，只补齐显式超时。
    OkHttpClient httpClient = Http.newDefaultClient().newBuilder()
        .connectTimeout(storage.getConnectTimeout())
        .readTimeout(storage.getReadTimeout())
        .writeTimeout(storage.getWriteTimeout())
        .callTimeout(storage.getRequestTimeout())
        // SDK在响应头返回后释放调度器占用；仍需跟踪到响应体关闭才能取消在途下载。
        .eventListener(new EventListener() {
          @Override
          public void callStart(Call call) {
            activeCalls.add(call);
            if (closing.get()) call.cancel();
          }

          @Override
          public void callEnd(Call call) {
            activeCalls.remove(call);
          }

          @Override
          public void callFailed(Call call, IOException failure) {
            activeCalls.remove(call);
          }
        })
        .build();
    client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey)
        .httpClient(httpClient, true).build();
  }

  /** 容器优雅停机结束后取消残留调用，由 SDK 关闭其拥有的线程池和空闲连接。 */
  @PreDestroy
  void closeClient() throws Exception {
    closing.set(true);
    activeCalls.forEach(Call::cancel);
    if (client != null) client.close();
  }

  @Override
  public void put(String objectKey, InputStream content) throws IOException {
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(
                  content, Long.valueOf(-1L), Long.valueOf(MAX_FILE_SIZE_BYTES))
              .build());
    } catch (Exception exception) {
      throw new IOException("object storage write failed", exception);
    }
  }

  @Override
  public InputStream open(String objectKey) throws IOException {
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception exception) {
      throw new IOException("object storage read failed", exception);
    }
  }

  @Override
  public boolean exists(String objectKey) throws IOException {
    try {
      client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
      return true;
    } catch (ErrorResponseException exception) {
      if ("NoSuchKey".equals(exception.errorResponse().code())) return false;
      throw new IOException("object storage metadata read failed", exception);
    } catch (Exception exception) {
      throw new IOException("object storage metadata read failed", exception);
    }
  }

  /** 删除指定业务记录。 */
  @Override
  public void delete(String objectKey) throws IOException {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception exception) {
      throw new IOException("object storage delete failed", exception);
    }
  }
}
