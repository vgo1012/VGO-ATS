package com.ezaigc.hr.integration.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.BucketStat;
import com.ezaigc.hr.integration.application.provider.ObjectStorageAdministrationPort;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.integration.domain.dto.ObjectStorageSnapshotDTO;
import com.ezaigc.hr.integration.storage.enums.ObjectStorageBodyState;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import io.minio.GetObjectArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** 阿里云 OSS 主存储；迁移期间可对历史对象回退读取旧 MinIO。 */
@Component
@ConditionalOnProperty(name = "hr.object-storage.mode", havingValue = "oss")
public class AliyunOssObjectStorageAdapter
    implements ObjectStoragePort, ObjectStorageAdministrationPort {
  @Resource private IntegrationProperties properties;
  @Resource private TaskScheduler taskScheduler;
  private final Object responseLifecycle = new Object();
  private final Set<ResponseBody> responses = ConcurrentHashMap.newKeySet();
  private boolean closed;
  private OSS client;
  private String bucket;
  private MinioClient legacyMinioClient;
  private String legacyMinioBucket;

  @PostConstruct
  void initializeClient() {
    IntegrationProperties.ObjectStorage storage = properties.getObjectStorage();
    if (!storage.hasValidTimeouts() || !storage.hasCompleteOssConfiguration()) {
      throw new IllegalStateException("OSS object storage configuration is invalid");
    }
    IntegrationProperties.Oss oss = storage.getOss();
    bucket = IntegrationProperties.requireConfigured(oss.getBucket(), "hr.object-storage.oss.bucket");
    CredentialsProvider credentials;
    try {
      credentials = CredentialsProviderFactory.newInstanceProfileCredentialsProvider(
          IntegrationProperties.requireConfigured(
              oss.getRoleName(), "hr.object-storage.oss.role-name"));
    } catch (Exception exception) {
      throw new IllegalStateException("ECS RAM role credentials provider initialization failed", exception);
    }
    ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
    configuration.setConnectionTimeout(Math.toIntExact(storage.getConnectTimeout().toMillis()));
    configuration.setSocketTimeout(Math.toIntExact(
        storage.getReadTimeout().compareTo(storage.getWriteTimeout()) >= 0
            ? storage.getReadTimeout().toMillis()
            : storage.getWriteTimeout().toMillis()));
    configuration.setRequestTimeoutEnabled(true);
    configuration.setRequestTimeout(Math.toIntExact(storage.getRequestTimeout().toMillis()));
    configuration.setSignatureVersion(SignVersion.V4);
    client = OSSClientBuilder.create()
        .endpoint(IntegrationProperties.requireConfigured(
            oss.getEndpoint(), "hr.object-storage.oss.endpoint"))
        .region(IntegrationProperties.requireConfigured(oss.getRegion(), "hr.object-storage.oss.region"))
        .credentialsProvider(credentials)
        .clientConfiguration(configuration)
        .build();
    if (storage.isLegacyMinioFallbackEnabled()) initializeLegacyMinio(storage);
  }

  private void initializeLegacyMinio(IntegrationProperties.ObjectStorage storage) {
    legacyMinioBucket = IntegrationProperties.requireConfigured(
        storage.getBucket(), "hr.object-storage.bucket");
    legacyMinioClient = MinioClient.builder()
        .endpoint(IntegrationProperties.requireConfigured(
            storage.getEndpoint(), "hr.object-storage.endpoint"))
        .credentials(
            IntegrationProperties.requireConfigured(
                storage.getAccessKey(), "hr.object-storage.access-key"),
            IntegrationProperties.requireConfigured(
                storage.getSecretKey(), "hr.object-storage.secret-key"))
        // 保留 SDK 的协议、重试与证书设置，回退请求也遵守单次 HTTP 截止。
        .httpClient(Http.newDefaultClient().newBuilder()
            .connectTimeout(storage.getConnectTimeout())
            .readTimeout(storage.getReadTimeout())
            .writeTimeout(storage.getWriteTimeout())
            .callTimeout(storage.getRequestTimeout())
            .build(), true)
        .build();
  }

  @PreDestroy
  void closeClient() throws Exception {
    ResponseBody[] outstanding;
    synchronized (responseLifecycle) {
      if (closed) return;
      closed = true;
      outstanding = responses.toArray(ResponseBody[]::new);
    }
    Exception failure = null;
    for (ResponseBody response : outstanding) {
      IOException aborted = response.finish(ObjectStorageBodyState.DESTROYED, true);
      if (aborted != null) {
        if (failure == null) failure = aborted;
        else failure.addSuppressed(aborted);
      }
    }
    try {
      if (client != null) client.shutdown();
    } catch (Exception exception) {
      if (failure == null) failure = exception;
      else failure.addSuppressed(exception);
    }
    try {
      if (legacyMinioClient != null) legacyMinioClient.close();
    } catch (Exception exception) {
      if (failure == null) failure = exception;
      else failure.addSuppressed(exception);
    }
    if (failure != null) throw failure;
  }

  @Override
  public void put(String objectKey, InputStream content) throws IOException {
    try {
      client.putObject(bucket, objectKey, content);
    } catch (Exception exception) {
      throw new IOException("object storage write failed", exception);
    }
  }

  @Override
  public InputStream open(String objectKey) throws IOException {
    long started = System.nanoTime();
    long budget = properties.getObjectStorage().getRequestTimeout().toNanos();
    synchronized (responseLifecycle) {
      if (closed) throw new IOException("object storage is closed");
    }
    try {
      var object = client.getObject(bucket, objectKey);
      return trackResponse(object.getObjectContent(), object::forcedClose, started, budget);
    } catch (OSSException exception) {
      if (isMissing(exception) && legacyMinioClient != null) {
        if (System.nanoTime() - started >= budget) throw deadlineExceeded();
        InputStream content = openLegacy(objectKey);
        return trackResponse(content, content::close, started, budget);
      }
      throw new IOException("object storage read failed", exception);
    } catch (IOException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IOException("object storage read failed", exception);
    }
  }

  private InputStream trackResponse(InputStream content, IoClose abort, long started, long budget)
      throws IOException {
    if (content == null) {
      IOException failure = new IOException("object storage response body is missing");
      try {
        abort.close();
      } catch (Exception cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    ResponseBody response = new ResponseBody(content, abort, started, budget);
    synchronized (responseLifecycle) {
      if (closed) {
        IOException failure = new IOException("object storage is closed");
        IOException cleanup = response.finish(ObjectStorageBodyState.DESTROYED, true);
        if (cleanup != null) failure.addSuppressed(cleanup);
        throw failure;
      }
      responses.add(response);
    }
    try {
      response.checkActive();
      ScheduledFuture<?> watchdog = Objects.requireNonNull(taskScheduler.schedule(
          () -> response.finish(ObjectStorageBodyState.TIMED_OUT, true),
          Instant.now().plusNanos(response.remainingNanos())));
      response.watchdog = watchdog;
      // 销毁/截止可能先于 schedule 返回；仍须移除刚入队的看门狗。
      if (response.state.get() != ObjectStorageBodyState.OPEN) watchdog.cancel(false);
      response.checkActive();
      return response;
    } catch (Exception exception) {
      IOException failure = exception instanceof IOException io
          ? io : new IOException("object storage deadline scheduling failed", exception);
      IOException cleanup = response.finish(ObjectStorageBodyState.CLOSED, true);
      if (cleanup != null) failure.addSuppressed(cleanup);
      throw failure;
    }
  }

  private static SocketTimeoutException deadlineExceeded() {
    return new SocketTimeoutException("object storage request deadline exceeded");
  }

  @FunctionalInterface
  private interface IoClose {
    void close() throws IOException;
  }

  /** SDK 总截止只覆盖响应头；响应体用同一 open 起点计时，并主动 abort HTTP 连接。 */
  private final class ResponseBody extends InputStream {
    private final InputStream content;
    private final IoClose abort;
    private final long started;
    private final long budget;
    private final AtomicReference<ObjectStorageBodyState> state =
        new AtomicReference<>(ObjectStorageBodyState.OPEN);
    private volatile ScheduledFuture<?> watchdog;

    private ResponseBody(InputStream content, IoClose abort, long started, long budget) {
      this.content = Objects.requireNonNull(content);
      this.abort = abort;
      this.started = started;
      this.budget = budget;
    }

    private long remainingNanos() {
      return Math.max(0L, budget - (System.nanoTime() - started));
    }

    private void checkActive() throws IOException {
      if (state.get() == ObjectStorageBodyState.OPEN && remainingNanos() == 0) {
        finish(ObjectStorageBodyState.TIMED_OUT, true);
      }
      switch (state.get()) {
        case TIMED_OUT -> throw deadlineExceeded();
        case CLOSED, DESTROYED -> throw new IOException("object storage response is closed");
        default -> { }
      }
    }

    private IOException finish(ObjectStorageBodyState terminal, boolean force) {
      if (!state.compareAndSet(ObjectStorageBodyState.OPEN, terminal)) return null;
      ScheduledFuture<?> scheduled = watchdog;
      if (scheduled != null) scheduled.cancel(false);
      responses.remove(this);
      try {
        // close() 可能排空尚未读取的 OSS 数据；提前结束必须使用公开 forcedClose()。
        if (force) abort.close();
        else content.close();
        return null;
      } catch (Exception exception) {
        return exception instanceof IOException io
            ? io : new IOException("object storage response cleanup failed", exception);
      }
    }

    @Override
    public int read() throws IOException {
      checkActive();
      if (state.get() == ObjectStorageBodyState.EOF) return -1;
      try {
        int value = content.read();
        checkActive();
        if (value == -1) finishEof();
        return value;
      } catch (IOException exception) {
        throw readFailed(exception);
      }
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      Objects.checkFromIndexSize(offset, length, bytes.length);
      checkActive();
      if (length == 0) return 0;
      if (state.get() == ObjectStorageBodyState.EOF) return -1;
      try {
        int count = content.read(bytes, offset, length);
        checkActive();
        if (count == -1) finishEof();
        return count;
      } catch (IOException exception) {
        throw readFailed(exception);
      }
    }

    @Override
    public long skip(long count) throws IOException {
      checkActive();
      if (count <= 0 || state.get() == ObjectStorageBodyState.EOF) return 0;
      try {
        long skipped = content.skip(count);
        checkActive();
        return skipped;
      } catch (IOException exception) {
        throw readFailed(exception);
      }
    }

    @Override
    public int available() throws IOException {
      checkActive();
      if (state.get() == ObjectStorageBodyState.EOF) return 0;
      try {
        int count = content.available();
        checkActive();
        return count;
      } catch (IOException exception) {
        throw readFailed(exception);
      }
    }

    private void finishEof() throws IOException {
      IOException failure = finish(ObjectStorageBodyState.EOF, false);
      if (failure != null) throw failure;
      checkActive();
    }

    private IOException readFailed(IOException failure) {
      IOException cleanup = finish(ObjectStorageBodyState.CLOSED, true);
      if (cleanup != null) failure.addSuppressed(cleanup);
      if (state.get() == ObjectStorageBodyState.TIMED_OUT) {
        IOException timeout = deadlineExceeded();
        timeout.addSuppressed(failure);
        return timeout;
      }
      return failure;
    }

    @Override
    public void close() throws IOException {
      IOException failure = finish(ObjectStorageBodyState.CLOSED, true);
      if (failure != null) throw failure;
    }
  }

  @Override
  public boolean exists(String objectKey) throws IOException {
    try {
      if (client.doesObjectExist(bucket, objectKey)) return true;
      return legacyMinioClient != null && existsInLegacy(objectKey);
    } catch (Exception exception) {
      throw new IOException("object storage metadata read failed", exception);
    }
  }

  @Override
  public void delete(String objectKey) throws IOException {
    try {
      client.deleteObject(bucket, objectKey);
      if (legacyMinioClient != null) {
        legacyMinioClient.removeObject(
            RemoveObjectArgs.builder().bucket(legacyMinioBucket).object(objectKey).build());
      }
    } catch (Exception exception) {
      throw new IOException("object storage delete failed", exception);
    }
  }

  @Override
  public ObjectStorageSnapshotDTO snapshot() {
    Instant sampledAt = Instant.now();
    try {
      BucketStat stat = client.getBucketStat(bucket);
      Instant statisticsUpdatedAt = stat.getLastModifiedTime() == null
          ? null : Instant.ofEpochSecond(stat.getLastModifiedTime());
      return new ObjectStorageSnapshotDTO(true, stat.getObjectCount(), stat.getStorageSize(),
          stat.getMultipartUploadCount(), statisticsUpdatedAt, sampledAt);
    } catch (Exception exception) {
      return new ObjectStorageSnapshotDTO(false, null, null, null, null, sampledAt);
    }
  }

  @Override
  public String bucketDisplayName() {
    if (bucket == null || bucket.length() < 5) return "****";
    return bucket.substring(0, 2) + "***" + bucket.substring(bucket.length() - 2);
  }

  private InputStream openLegacy(String objectKey) throws IOException {
    try {
      return legacyMinioClient.getObject(
          GetObjectArgs.builder().bucket(legacyMinioBucket).object(objectKey).build());
    } catch (Exception exception) {
      throw new IOException("legacy object storage read failed", exception);
    }
  }

  private boolean existsInLegacy(String objectKey) throws Exception {
    try {
      legacyMinioClient.statObject(
          StatObjectArgs.builder().bucket(legacyMinioBucket).object(objectKey).build());
      return true;
    } catch (ErrorResponseException exception) {
      if ("NoSuchKey".equals(exception.errorResponse().code())) return false;
      throw exception;
    }
  }

  private static boolean isMissing(OSSException exception) {
    return "NoSuchKey".equals(exception.getErrorCode())
        || "NoSuchObject".equals(exception.getErrorCode());
  }

}
