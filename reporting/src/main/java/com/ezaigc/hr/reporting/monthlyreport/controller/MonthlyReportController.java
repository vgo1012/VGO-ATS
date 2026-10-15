package com.ezaigc.hr.reporting.monthlyreport.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportApplicationManager;
import com.ezaigc.hr.reporting.monthlyreport.controller.validation.ValidMonthlyReportFile;
import com.ezaigc.hr.reporting.monthlyreport.domain.request.GenerateMonthlyReportRequest;
import com.ezaigc.hr.reporting.monthlyreport.domain.request.MonthlyReportActionRequest;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportDetailResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportDownloadResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportPageResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/monthly-reports")
public class MonthlyReportController {
    @Resource private MonthlyReportApplicationManager manager;
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MonthlyReportDetailResponse> upload(@ValidMonthlyReportFile @RequestPart MultipartFile offerFile, @ValidMonthlyReportFile @RequestPart MultipartFile demandFile,
            @Pattern(regexp = "[0-9]{4}-[0-9]{2}") @RequestParam String month,
            @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") @RequestParam String asOfDate,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(manager.upload(offerFile, demandFile, month, asOfDate, key));
    }
    @GetMapping
    public MonthlyReportPageResponse list(@Pattern(regexp = "[0-9]{4}-[0-9]{2}") @RequestParam(required = false) String month,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(defaultValue = "20") int limit) { return manager.list(month, cursor, limit); }
    @GetMapping("/{id}")
    public MonthlyReportDetailResponse detail(@PlatformIdentifier @PathVariable String id) { return manager.detail(id); }
    @PostMapping("/{id}/generate")
    public ResponseEntity<MonthlyReportDetailResponse> generate(@PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody GenerateMonthlyReportRequest request, @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(manager.generate(id, request, key));
    }
    @PostMapping("/{id}/retry")
    public MonthlyReportDetailResponse retry(@PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody MonthlyReportActionRequest request, @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) { return manager.retry(id, request, key); }
    @PostMapping("/{id}/cancel")
    public MonthlyReportDetailResponse cancel(@PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody MonthlyReportActionRequest request, @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) { return manager.cancel(id, request, key); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody MonthlyReportActionRequest request) {
        manager.delete(id, request);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PlatformIdentifier @PathVariable String id) {
        MonthlyReportDownloadResponse file = manager.download(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(file.sizeBytes())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(new InputStreamResource(file.stream()));
    }
}
