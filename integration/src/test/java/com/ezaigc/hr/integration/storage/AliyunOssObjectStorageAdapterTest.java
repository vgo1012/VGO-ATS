package com.ezaigc.hr.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSClientBuilder.OSSClientBuilderImpl;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.InstanceProfileCredentialsProvider;
import com.aliyun.oss.model.BucketStat;
import com.aliyun.oss.model.OSSObject;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

class AliyunOssObjectStorageAdapterTest {

  @ParameterizedTest
  @ValueSource(strings = {"read", "skip", "drip"})
  void totalDeadlineBoundsOssBodyAfterSuccessfulHeaders(String operation) throws Exception {
    try (var fixture = new LegacyFixture("normal", false)) {
      var body = new BlockingOssBody(operation.equals("drip"));
      var object = mock(OSSObject.class);
      when(object.getObjectContent()).thenReturn(body);
      doAnswer(call -> { body.close(); return null; }).when(object).forcedClose();
      doReturn(object).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      var call = fixture.executor.submit(() -> {
        try (var content = fixture.adapter.open("synthetic.txt")) {
          assertThat(content.read()).isEqualTo('x');
          body.started.countDown();
          if (operation.equals("skip")) return content.skip(63);
          return (long) content.readAllBytes().length;
        }
      });
      try {
        assertThat(body.started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> call.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
        assertThat(body.closed.getCount()).as("总截止主动中止响应体").isZero();
        verify(object).forcedClose();
      } finally {
        body.close();
        call.cancel(true);
      }
    }
  }

  @Test
  void adapterDestroyAbortsOutstandingOssResponseBodies() throws Exception {
    try (var fixture = new LegacyFixture("normal", false)) {
      var body = new BlockingOssBody(false);
      var object = mock(OSSObject.class);
      when(object.getObjectContent()).thenReturn(body);
      doAnswer(call -> { body.close(); return null; }).when(object).forcedClose();
      doReturn(object).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      var content = fixture.adapter.open("synthetic.txt");
      assertThat(content.read()).isEqualTo('x');
      var call = fixture.executor.submit(() -> { return content.read(); });
      try {
        fixture.context.close();
        assertThatThrownBy(() -> call.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
        assertThat(body.closed.getCount()).isZero();
        verify(object).forcedClose();
      } finally {
        body.close();
        call.cancel(true);
      }
    }
  }

  @Test
  void totalDeadlineRunsWhileTheCallerHasNotStartedReading() throws Exception {
    try (var fixture = new LegacyFixture("normal", false)) {
      var body = new BlockingOssBody(false);
      var object = mock(OSSObject.class);
      when(object.getObjectContent()).thenReturn(body);
      doAnswer(call -> { body.close(); return null; }).when(object).forcedClose();
      doReturn(object).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      try (var content = fixture.adapter.open("synthetic.txt")) {
        assertThat(body.closed.await(2, TimeUnit.SECONDS))
            .as("不依赖下一次 read 才检查总截止").isTrue();
        assertThatThrownBy(content::read).isInstanceOf(IOException.class);
      } finally {
        body.close();
      }
    }
  }

  private static final class BlockingOssBody extends InputStream {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);
    private final AtomicInteger bytes = new AtomicInteger();
    private final boolean drip;

    private BlockingOssBody(boolean drip) {
      this.drip = drip;
    }

    @Override
    public int read() throws IOException {
      if (bytes.getAndIncrement() == 0) return 'x';
      try {
        if (closed.await(drip ? 50 : 5_000, TimeUnit.MILLISECONDS)) {
          throw new IOException("synthetic response aborted");
        }
        return drip ? 'x' : -1;
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IOException("synthetic response interrupted", interrupted);
      }
    }

    @Override
    public long skip(long count) throws IOException {
      read();
      return count;
    }

    @Override
    public void close() {
      closed.countDown();
    }
  }

  @Test
  void delegatesObjectOperationsAndReturnsDelayedBucketStatistics() throws Exception {
    OSS client = mock(OSS.class);
    OSSObject object = mock(OSSObject.class);
    InputStream content = new ByteArrayInputStream(new byte[] {1, 2, 3});
    when(object.getObjectContent()).thenReturn(content);
    when(client.getObject("private-bucket", "resume/key")).thenReturn(object);
    when(client.doesObjectExist("private-bucket", "resume/key")).thenReturn(true);
    when(client.getBucketStat("private-bucket"))
        .thenReturn(new BucketStat(4096L, 12L, 2L).withLastModifiedTime(1_700_000_000L));
    AliyunOssObjectStorageAdapter adapter = adapter(client);

    ByteArrayInputStream upload = new ByteArrayInputStream(new byte[] {9});
    adapter.put("resume/key", upload);
    try (var download = adapter.open("resume/key")) {
      assertThat(download.readAllBytes()).containsExactly(1, 2, 3);
    }
    assertThat(adapter.exists("resume/key")).isTrue();
    adapter.delete("resume/key");
    var snapshot = adapter.snapshot();

    verify(client).putObject("private-bucket", "resume/key", upload);
    verify(client).deleteObject("private-bucket", "resume/key");
    assertThat(snapshot.isAvailable()).isTrue();
    assertThat(snapshot.getObjectCount()).isEqualTo(12L);
    assertThat(snapshot.getStorageBytes()).isEqualTo(4096L);
    assertThat(snapshot.getMultipartUploadCount()).isEqualTo(2L);
    assertThat(snapshot.getStatisticsUpdatedAt()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
    assertThat(adapter.bucketDisplayName()).isEqualTo("pr***et");
  }

  @Test
  void responseHeaderTimeIsPartOfTheOpenDeadline() throws Exception {
    try (var fixture = new LegacyFixture("normal", false)) {
      var body = new BlockingOssBody(false);
      var object = mock(OSSObject.class);
      when(object.getObjectContent()).thenReturn(body);
      doAnswer(call -> { body.close(); return null; }).when(object).forcedClose();
      doAnswer(call -> {
        Thread.sleep(600);
        return object;
      }).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      assertThatThrownBy(() -> fixture.adapter.open("synthetic.txt"))
          .isInstanceOf(IOException.class).hasMessageContaining("deadline exceeded");
      assertThat(body.closed.getCount()).isZero();
      verify(object).forcedClose();
      assertThat(fixture.scheduler().getScheduledThreadPoolExecutor().getQueue()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"eof", "close", "error"})
  void releasingResponsesCancelsWatchdogsAndRemovesOwnedBodies(String reason) throws Exception {
    try (var fixture = new LegacyFixture("normal", false)) {
      var body = mock(InputStream.class);
      var object = mock(OSSObject.class);
      when(object.getObjectContent()).thenReturn(body);
      when(body.read()).thenReturn(-1);
      if (reason.equals("error")) when(body.read()).thenThrow(new IOException("synthetic read failed"));
      doReturn(object).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      try (var content = fixture.adapter.open("synthetic.txt")) {
        if (reason.equals("eof")) assertThat(content.read()).isEqualTo(-1);
        else if (reason.equals("error")) {
          assertThatThrownBy(content::read).isInstanceOf(IOException.class);
        }
      }
      assertThat(fixture.scheduler().getScheduledThreadPoolExecutor().getQueue()).isEmpty();
      fixture.context.close();
      if (reason.equals("eof")) verify(body).close();
      else verify(object).forcedClose();
    }
  }

  @Test
  void responseArrivingAfterAdapterDestroyIsAbortedAndNeverRegistered() throws Exception {
    try (var fixture = new LegacyFixture("normal", false)) {
      var headersEntered = new CountDownLatch(1);
      var headersReleased = new CountDownLatch(1);
      var object = mock(OSSObject.class);
      when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(new byte[] {1}));
      doAnswer(call -> {
        headersEntered.countDown();
        if (!headersReleased.await(2, TimeUnit.SECONDS)) throw new IOException("synthetic headers not released");
        return object;
      }).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      var call = fixture.executor.submit(() -> fixture.adapter.open("synthetic.txt"));
      try {
        assertThat(headersEntered.await(2, TimeUnit.SECONDS)).isTrue();
        fixture.context.close();
        headersReleased.countDown();
        assertThatThrownBy(() -> call.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
        verify(object).forcedClose();
        assertThatThrownBy(() -> fixture.adapter.open("another.txt"))
            .isInstanceOf(IOException.class).hasMessageContaining("closed");
      } finally {
        headersReleased.countDown();
        call.cancel(true);
      }
    }
  }

  @Test
  void schedulingRejectionAbortsTheResponseInsteadOfReturningAnUnboundedStream() throws Exception {
    var client = mock(OSS.class);
    var object = mock(OSSObject.class);
    when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(client.getObject("private-bucket", "resume/key")).thenReturn(object);
    var adapter = adapter(client);
    var scheduler = mock(TaskScheduler.class);
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenThrow(new RejectedExecutionException("synthetic scheduler unavailable"));
    ReflectionTestUtils.setField(adapter, "taskScheduler", scheduler);
    assertThatThrownBy(() -> adapter.open("resume/key")).isInstanceOf(IOException.class)
        .hasMessageContaining("scheduling failed");
    adapter.closeClient();
    verify(object).forcedClose();
  }

  @Test
  void deadlineFiringBeforeSchedulerReturnsStillCancelsTheEnqueuedFuture() throws Exception {
    var client = mock(OSS.class);
    var object = mock(OSSObject.class);
    when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(client.getObject("private-bucket", "resume/key")).thenReturn(object);
    var adapter = adapter(client);
    var scheduler = mock(TaskScheduler.class);
    var watchdog = mock(ScheduledFuture.class);
    when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
      call.getArgument(0, Runnable.class).run();
      return watchdog;
    });
    ReflectionTestUtils.setField(adapter, "taskScheduler", scheduler);
    assertThatThrownBy(() -> adapter.open("resume/key")).isInstanceOf(IOException.class)
        .hasMessageContaining("deadline exceeded");
    adapter.closeClient();
    verify(watchdog).cancel(false);
    verify(object).forcedClose();
  }

  @Test
  void hidesProviderFailuresFromTheAdministrationSnapshot() {
    OSS client = mock(OSS.class);
    when(client.getBucketStat("private-bucket")).thenThrow(new IllegalStateException("signed secret"));

    var snapshot = adapter(client).snapshot();

    assertThat(snapshot.isAvailable()).isFalse();
    assertThat(snapshot.getObjectCount()).isNull();
    assertThat(snapshot.getStorageBytes()).isNull();
    assertThat(snapshot.getMultipartUploadCount()).isNull();
    assertThat(snapshot.getStatisticsUpdatedAt()).isNull();
  }

  @Test
  void checksLegacyMinioAndDeletesBothCopiesDuringMigration() throws Exception {
    OSS client = mock(OSS.class);
    MinioClient legacy = mock(MinioClient.class);
    when(client.doesObjectExist("private-bucket", "resume/legacy")).thenReturn(false);
    AliyunOssObjectStorageAdapter adapter = adapter(client);
    ReflectionTestUtils.setField(adapter, "legacyMinioClient", legacy);
    ReflectionTestUtils.setField(adapter, "legacyMinioBucket", "legacy-bucket");

    assertThat(adapter.exists("resume/legacy")).isTrue();
    adapter.delete("resume/legacy");

    verify(legacy).statObject(argThat((StatObjectArgs args) ->
        "legacy-bucket".equals(args.bucket()) && "resume/legacy".equals(args.object())));
    verify(client).deleteObject("private-bucket", "resume/legacy");
    verify(legacy).removeObject(argThat((RemoveObjectArgs args) ->
        "legacy-bucket".equals(args.bucket()) && "resume/legacy".equals(args.object())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"headers", "body", "drip"})
  void requestDeadlineBoundsLegacyHeadersAndEntireResponseBody(String phase) throws Exception {
    try (var fixture = new LegacyFixture(phase, true)) {
      var firstByte = new CountDownLatch(phase.equals("headers") ? 0 : 1);
      var call = fixture.executor.submit(() -> {
        if (phase.equals("headers")) return fixture.adapter.exists("synthetic.txt");
        try (var content = fixture.adapter.open("synthetic.txt")) {
          assertThat(content.read()).isEqualTo('x');
          firstByte.countDown();
          return content.readAllBytes().length == 63;
        }
      });
      try {
        assertThat(fixture.started.await(3, TimeUnit.SECONDS)).as("回退SDK已进入实际HTTP阶段").isTrue();
        assertThat(firstByte.await(2, TimeUnit.SECONDS)).as("回退下载已读到对象首字节").isTrue();
        assertThatThrownBy(() -> call.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
      } finally {
        fixture.release.countDown();
        fixture.server.stop(0);
        call.cancel(true);
      }
    }
  }

  @Test
  void appliesAllConfiguredTimeoutsToTheActualLegacySdkClient() throws Exception {
    try (var fixture = new LegacyFixture("normal", true)) {
      var http = fixture.legacyHttp();
      assertThat(http.callTimeoutMillis()).isEqualTo(500);
      assertThat(http.connectTimeoutMillis()).isEqualTo(1000);
      assertThat(http.readTimeoutMillis()).isEqualTo(5000);
      assertThat(http.writeTimeoutMillis()).isEqualTo(5000);
    }
  }

  @Test
  void normalLegacyReadPreservesBytesAndClosesOwnedHttpResources() throws Exception {
    try (var fixture = new LegacyFixture("normal", true)) {
      var http = fixture.legacyHttp();
      assertThat(fixture.adapter.exists("synthetic.txt")).isTrue();
      try (var content = fixture.adapter.open("synthetic.txt")) {
        assertThat(content.readAllBytes()).isEqualTo("x".repeat(64).getBytes(StandardCharsets.UTF_8));
      }
      assertThat(http.dispatcher().executorService().isShutdown()).isFalse();
      assertThat(http.connectionPool().connectionCount()).isPositive();
      fixture.context.close();
      assertThat(http.dispatcher().executorService().isShutdown()).isTrue();
      assertThat(http.connectionPool().connectionCount()).isZero();
    }
  }

  @Test
  void existingOssObjectsAndNonMissingErrorsDoNotUseLegacyFallback() throws Exception {
    try (var fixture = new LegacyFixture("headers", true)) {
      var object = mock(OSSObject.class);
      var bytes = new byte[] {1, 2, 3};
      when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(bytes));
      doReturn(object).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      doReturn(true).when(fixture.primary).doesObjectExist("synthetic-primary", "synthetic.txt");
      assertThat(fixture.adapter.exists("synthetic.txt")).isTrue();
      try (var content = fixture.adapter.open("synthetic.txt")) {
        assertThat(content.readAllBytes()).isEqualTo(bytes);
      }
      var denied = mock(OSSException.class);
      when(denied.getErrorCode()).thenReturn("AccessDenied");
      doThrow(denied).when(fixture.primary).getObject("synthetic-primary", "synthetic.txt");
      assertThatThrownBy(() -> fixture.adapter.open("synthetic.txt"))
          .isInstanceOf(IOException.class).hasCause(denied);
      assertThat(fixture.requests.get()).isZero();
    }
  }

  @Test
  void disabledFallbackCreatesNoLegacyClientOrRequests() throws Exception {
    try (var fixture = new LegacyFixture("headers", false)) {
      assertThat(ReflectionTestUtils.getField(fixture.adapter, "legacyMinioClient")).isNull();
      assertThat(fixture.adapter.exists("synthetic.txt")).isFalse();
      assertThatThrownBy(() -> fixture.adapter.open("synthetic.txt"))
          .isInstanceOf(IOException.class).hasCause(fixture.missing);
      assertThat(fixture.requests.get()).isZero();
    }
  }

  /** OSS及ECS凭据入口使用替身，真实MinIO SDK只连接随机端口的回环S3夹具。 */
  private static final class LegacyFixture implements AutoCloseable {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpServer server;
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private final OSS primary = mock(OSS.class);
    private final OSSException missing = mock(OSSException.class);
    private final String phase;
    private AnnotationConfigApplicationContext context;
    private AliyunOssObjectStorageAdapter adapter;

    private LegacyFixture(String phase, boolean fallback) throws Exception {
      this.phase = phase;
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      assertThat(server.getAddress().getAddress().isLoopbackAddress()).isTrue();
      server.setExecutor(executor);
      server.createContext("/", this::respond);
      server.start();
      var properties = new IntegrationProperties();
      var storage = properties.getObjectStorage();
      storage.setMode("oss");
      storage.setLegacyMinioFallbackEnabled(fallback);
      storage.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
      storage.setBucket("synthetic-test");
      storage.setAccessKey("synthetic-access");
      storage.setSecretKey("synthetic-secret-only");
      storage.setConnectTimeout(Duration.ofSeconds(1));
      storage.setReadTimeout(Duration.ofSeconds(5));
      storage.setWriteTimeout(Duration.ofSeconds(5));
      storage.setRequestTimeout(Duration.ofMillis(500));
      var oss = storage.getOss();
      oss.setEndpoint("https://synthetic-oss.invalid");
      oss.setRegion("cn-synthetic");
      oss.setBucket("synthetic-primary");
      oss.setRoleName("synthetic-role");
      when(missing.getErrorCode()).thenReturn("NoSuchKey");
      when(primary.getObject(anyString(), anyString())).thenThrow(missing);
      when(primary.doesObjectExist(anyString(), anyString())).thenReturn(false);
      var builder = mock(OSSClientBuilderImpl.class, RETURNS_SELF);
      when(builder.build()).thenReturn(primary);
      try (var credentials = mockStatic(CredentialsProviderFactory.class);
          var builders = mockStatic(OSSClientBuilder.class)) {
        credentials.when(() -> CredentialsProviderFactory.newInstanceProfileCredentialsProvider("synthetic-role"))
            .thenReturn(mock(InstanceProfileCredentialsProvider.class));
        builders.when(OSSClientBuilder::create).thenReturn(builder);
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("synthetic-oss", Map.of("hr.object-storage.mode", "oss")));
        context.registerBean(IntegrationProperties.class, () -> properties);
        context.registerBean("taskScheduler", ThreadPoolTaskScheduler.class, () -> {
          var scheduler = new ThreadPoolTaskScheduler();
          scheduler.setPoolSize(2);
          scheduler.setThreadNamePrefix("oss-deadline-test-");
          scheduler.setRemoveOnCancelPolicy(true);
          return scheduler;
        });
        context.register(AliyunOssObjectStorageAdapter.class);
        context.refresh();
        adapter = context.getBean(AliyunOssObjectStorageAdapter.class);
      } catch (Exception failure) {
        close();
        throw failure;
      }
    }

    private OkHttpClient legacyHttp() {
      var client = (MinioClient) ReflectionTestUtils.getField(adapter, "legacyMinioClient");
      var async = ReflectionTestUtils.getField(client, "asyncClient");
      return (OkHttpClient) ReflectionTestUtils.getField(async, "httpClient");
    }

    private ThreadPoolTaskScheduler scheduler() {
      return context.getBean(ThreadPoolTaskScheduler.class);
    }

    private void respond(HttpExchange exchange) throws IOException {
      requests.incrementAndGet();
      try {
        if (exchange.getRequestURI().getRawQuery() != null
            && exchange.getRequestURI().getRawQuery().startsWith("location")) {
          var bytes = "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">us-east-1</LocationConstraint>"
              .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/xml");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          return;
        }
        if (!exchange.getRequestURI().getPath().equals("/synthetic-test/synthetic.txt")) {
          exchange.sendResponseHeaders(400, -1);
          return;
        }
        if (phase.equals("headers")) {
          started.countDown();
          release.await(5, TimeUnit.SECONDS);
        }
        exchange.getResponseHeaders().set("Last-Modified", "Mon, 07 Sep 2026 00:00:00 GMT");
        exchange.getResponseHeaders().set("ETag", "\"synthetic-etag\"");
        if (exchange.getRequestMethod().equals("HEAD")) {
          exchange.getResponseHeaders().set("Content-Length", "64");
          exchange.sendResponseHeaders(200, -1);
          return;
        }
        exchange.sendResponseHeaders(200, 64);
        exchange.getResponseBody().write('x');
        exchange.getResponseBody().flush();
        started.countDown();
        if (phase.equals("body")) release.await(5, TimeUnit.SECONDS);
        for (int index = 1; index < 64; index++) {
          if (phase.equals("drip") && release.await(100, TimeUnit.MILLISECONDS)) break;
          exchange.getResponseBody().write('x');
          exchange.getResponseBody().flush();
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    }

    @Override
    public void close() {
      release.countDown();
      server.stop(0);
      try {
        if (context != null) context.close();
      } finally {
        executor.close();
      }
    }
  }

  private static AliyunOssObjectStorageAdapter adapter(OSS client) {
    AliyunOssObjectStorageAdapter adapter = new AliyunOssObjectStorageAdapter();
    ReflectionTestUtils.setField(adapter, "client", client);
    ReflectionTestUtils.setField(adapter, "bucket", "private-bucket");
    ReflectionTestUtils.setField(adapter, "properties", new IntegrationProperties());
    var scheduler = mock(TaskScheduler.class);
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenReturn(mock(ScheduledFuture.class));
    ReflectionTestUtils.setField(adapter, "taskScheduler", scheduler);
    return adapter;
  }
}
