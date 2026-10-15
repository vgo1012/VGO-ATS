package com.ezaigc.hr.integration.config;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.INDEX_NAME_PATTERN;

import com.ezaigc.hr.common.core.util.CommonUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 集成配置属性对象，负责承载并校验相关配置。
 */
@Validated
@ConfigurationProperties("hr")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationProperties {

    private @Valid ObjectStorage objectStorage =
            new ObjectStorage();
    private @Valid MalwareScan malwareScan = new MalwareScan();
    private @Valid Ai ai = new Ai();
    private @Valid Search search = new Search();
    private @Valid DocumentExtraction documentExtraction = new DocumentExtraction();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObjectStorage {

        @Pattern(regexp = "local|minio|oss")
        private String mode = "local";

        private String endpoint;
        @ToString.Exclude
        private String accessKey;
        @ToString.Exclude
        private String secretKey;
        private String bucket;
        private String localRoot = ".runtime/objects";
        private boolean legacyMinioFallbackEnabled;
        private @Valid Oss oss = new Oss();

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(2);
        @NotNull
        private Duration readTimeout = Duration.ofSeconds(30);
        @NotNull
        private Duration writeTimeout = Duration.ofSeconds(30);
        /** 单次 S3 HTTP 请求的总上限，包含响应体；分片上传的各请求独立计时。 */
        @NotNull
        private Duration requestTimeout = Duration.ofMinutes(2);

        @AssertTrue(message = "object storage timeouts must be positive and fit HTTP millisecond limits")
        public boolean hasValidTimeouts() {
            return validSocketTimeout(connectTimeout)
                    && validSocketTimeout(readTimeout)
                    && validSocketTimeout(writeTimeout)
                    && validSocketTimeout(requestTimeout);
        }

        @AssertTrue(message = "OSS mode requires an HTTPS endpoint, region, bucket and ECS RAM role")
        public boolean hasCompleteOssConfiguration() {
            return !"oss".equals(mode)
                    || oss != null
                    && "ecs-ram-role".equals(oss.getCredentialMode())
                    && hasValidOssEndpoint(oss.getEndpoint())
                    && !CommonUtils.isBlank(oss.getRegion())
                    && !CommonUtils.isBlank(oss.getBucket())
                    && !CommonUtils.isBlank(oss.getRoleName());
        }

        private static boolean hasValidOssEndpoint(String value) {
            if (CommonUtils.isBlank(value)) {
                return false;
            }
            try {
                URI endpoint = URI.create(value.strip());
                String path = endpoint.getPath();
                return "https".equalsIgnoreCase(endpoint.getScheme())
                        && endpoint.getHost() != null
                        && endpoint.getUserInfo() == null
                        && endpoint.getQuery() == null
                        && endpoint.getFragment() == null
                        && (CommonUtils.isBlank(path) || "/".equals(path));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Oss {

        @Pattern(regexp = "ecs-ram-role")
        private String credentialMode = "ecs-ram-role";

        @Size(max = 2048)
        private String endpoint;

        @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,62}")
        private String region;

        @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
        private String bucket;

        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
        private String roleName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MalwareScan {

        @Pattern(regexp = "development|clamav")
        private String mode = "development";

        private String host;

        @Min(1)
        @Max(65535)
        private int port = 3310;

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(2);
        /** 整个 INSTREAM 文件发送阶段的上限，不替代连接或回复读取超时。 */
        @NotNull
        private Duration writeTimeout = Duration.ofMinutes(2);
        @NotNull
        private Duration readTimeout = Duration.ofMinutes(2);

        @AssertTrue(
                message = "malware scan timeouts must be positive and fit socket millisecond limits")
        public boolean hasValidTimeouts() {
            return validSocketTimeout(connectTimeout)
                    && validSocketTimeout(writeTimeout)
                    && validSocketTimeout(readTimeout);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ai {

        private static final String DEVELOPMENT_ARCHIVE_KEY =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

        @NotBlank
        @ToString.Exclude
        private String archiveKey = DEVELOPMENT_ARCHIVE_KEY;
        @Valid
        private Parsing parsing = new Parsing(false);

        @AssertTrue(
                message =
                        "hr.ai.archive-key must be a deployment-specific 32-byte base64 key when AI parsing is enabled")
        public boolean isArchiveKeyDeploymentSpecificWhenParsingEnabled() {
            if (parsing == null || !parsing.isEnabled()) {
                return true;
            }
            try {
                byte[] decoded = Base64.getDecoder().decode(archiveKey);
                // 解析关闭时允许开发默认值；启用后必须拒绝任何等价编码的公开全零密钥。
                return decoded.length == 32 && !allZero(decoded);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static boolean allZero(byte[] value) {
            for (byte item : value) {
                if (item != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parsing {

        private boolean enabled;
    }

    @Data
    @NoArgsConstructor
    public static class Search {

        @Pattern(regexp = "memory|elasticsearch")
        private String mode = "memory";

        private Boolean workerEnabled = Boolean.TRUE;

        @Valid
        private Elasticsearch elasticsearch = new Elasticsearch();

        public Search(String mode, Boolean workerEnabled, Elasticsearch elasticsearch) {
            this.mode = CommonUtils.isBlank(mode) ? "memory" : mode;
            this.workerEnabled = workerEnabled == null ? Boolean.TRUE : workerEnabled;
            this.elasticsearch =
                    elasticsearch == null ? new Elasticsearch() : elasticsearch;
        }

        @AssertTrue(
                message =
                        "hr.search.elasticsearch.base-url must be an HTTP(S) endpoint without credentials")
        public boolean isElasticsearchEndpointValidWhenEnabled() {
            return !"elasticsearch".equals(mode)
                    || elasticsearch != null && elasticsearch.hasValidEndpoint();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Elasticsearch {

        @Size(max = 2048)
        private String baseUrl;

        @NotBlank
        @Pattern(regexp = INDEX_NAME_PATTERN)
        private String alias = "resume-facts-current";

        @ToString.Exclude
        @Size(max = 8192)
        private String apiKey;
        @Size(max = 512)
        private String username;
        @ToString.Exclude
        @Size(max = 8192)
        private String password;
        @NotNull
        private Duration timeout = Duration.ofSeconds(10);
        @Min(0)
        @Max(20)
        private int numberOfReplicas = 1;

        @AssertTrue(message = "Elasticsearch timeout must be positive")
        public boolean hasValidTimeout() {
            return timeout != null && !timeout.isZero() && !timeout.isNegative();
        }

        @AssertTrue(message = "Elasticsearch username and password must be configured together")
        public boolean hasCompleteBasicCredentials() {
            return CommonUtils.isBlank(username) == CommonUtils.isBlank(password);
        }

        public boolean hasValidEndpoint() {
            if (CommonUtils.isBlank(baseUrl)) {
                return false;
            }
            try {
                URI endpoint = URI.create(baseUrl.strip());
                String scheme = endpoint.getScheme();
                return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && endpoint.getHost() != null
                        && endpoint.getUserInfo() == null
                        && endpoint.getQuery() == null
                        && endpoint.getFragment() == null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentExtraction {

        /** 单文档页数上限；允许部署后调优，但必须保持有限边界。 */
        @Min(1)
        @Max(1000)
        private int maximumPages = 100;

        /** 单文档最终提取字符总量上限；同时用于约束原生解析及外部识别响应。 */
        @Min(1)
        @Max(2_000_000)
        private int maximumExtractedCharacters = 500_000;

        /** DOCX 容器允许解压出的总字节数，防止压缩包以小体积放大堆内存和 CPU 消耗。 */
        @Min(1_048_576)
        @Max(268_435_456)
        private long maximumContainerExpandedBytes = 67_108_864L;

        /** DOCX ZIP 条目数上限，防止大量微小条目拖垮容器解析。 */
        @Min(1)
        @Max(10_000)
        private int maximumContainerEntries = 4_096;

        /** PDF 独立资源节点上限，共享引用只计一次，兼容拆分字体而不放开无界对象图。 */
        @Min(1)
        @Max(100_000)
        private int maximumPdfResourceNodes = 65_536;

        /** PDF 解码字节及原图栅格预留的累计预算；不代表 JVM 峰值内存。 */
        @Min(1_048_576)
        @Max(268_435_456)
        private long maximumPdfExpandedBytes = 268_435_456L;

        /** 原图和蒙版解码前的像素预算，允许高分辨率扫描但保持有限边界。 */
        @Min(1)
        @Max(32_000_000)
        private long maximumSourceImagePixels = 32_000_000L;

        /** 原图单边上限独立于页面输出尺寸，防止极端长宽比。 */
        @Min(1)
        @Max(20_000)
        private int maximumSourceImageDimension = 20_000;

        /** PDF 低质量页面栅格化的 DPI；像素门禁会在分配图像内存前执行。 */
        @Min(1)
        @Max(300)
        private int renderDpi = 150;

        /** 单页栅格像素总量上限；默认约束 RGB 图像主体不超过 32 MB。 */
        @Min(1)
        @Max(16_000_000)
        private long maximumRenderedPagePixels = 8_000_000L;

        /** 单页栅格任一边的像素上限，防止极端长宽比触发图像库维度异常。 */
        @Min(1)
        @Max(10_000)
        private int maximumRenderedPageDimension = 10_000;

        @Min(1)
        private int minimumPageCharacters = 40;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumPrintableRatio = 0.80;

    }

    public static String requireConfigured(String value, String propertyName) {
        if (CommonUtils.isBlank(value)) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value.trim();
    }

    private static boolean validSocketTimeout(Duration value) {
        return value != null
                && value.compareTo(Duration.ofMillis(1)) >= 0
                && value.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0;
    }
}
