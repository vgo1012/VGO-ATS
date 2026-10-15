package com.ezaigc.hr.talent.controller;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_CANDIDATE_QUERY_LENGTH;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_DOWNLOAD_TOKEN_INPUT_PATTERN;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_DOWNLOAD_TOKEN_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.controller.validation.ValidResumeFile;
import com.ezaigc.hr.talent.domain.request.TalentIntakeAssignRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeBatchRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeBatchJdRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeCandidateRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeChannelRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeConfirmRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeImportRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeConflictRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeUpdateChannelRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeVersionReasonRequest;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import com.ezaigc.hr.talent.domain.response.DownloadGrantResponse;
import com.ezaigc.hr.talent.domain.response.DownloadedResumeResponse;
import com.ezaigc.hr.talent.domain.response.IdentityConflictResponse;
import com.ezaigc.hr.talent.domain.response.IntakeBatchResponse;
import com.ezaigc.hr.talent.domain.response.InterviewMaterialResponse;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.domain.response.ResumePreviewResponse;
import com.ezaigc.hr.talent.domain.response.ResumeVersionResponse;
import com.ezaigc.hr.talent.domain.response.SourceChannelResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1")
public class TalentIntakeController {

    @Resource
    private TalentIntakeApplicationManager manager;
    @Resource
    private TalentIntakeAssembler assembler;

    @GetMapping("/source-channels")
    CursorPage<SourceChannelResponse> channels(
            @OptionalFilterCode @RequestParam(required = false) String status,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return manager.channelResponses(status, cursor, limit);
    }

    /**
     * 创建通知或沟通渠道。
     */
    @PostMapping("/source-channels")
    @ResponseStatus(HttpStatus.CREATED)
    SourceChannelResponse createChannel(
            @Valid @RequestBody TalentIntakeChannelRequest r,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return manager.createChannel(assembler.toDTO(r), key);
    }

    @GetMapping("/source-channels/{id}")
    SourceChannelResponse channel(@PlatformIdentifier @PathVariable String id) {
        return manager.channelResponse(id);
    }

    /**
     * 更新通知或沟通渠道。
     */
    @PatchMapping("/source-channels/{id}")
    SourceChannelResponse updateChannel(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeUpdateChannelRequest r) {
        return manager.updateChannel(id, assembler.toDTO(r));
    }

    /**
     * 停用通知或沟通渠道。
     */
    @PostMapping("/source-channels/{id}/disable")
    SourceChannelResponse disableChannel(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        return manager.disableChannel(id, assembler.toDTO(r));
    }

    /** 删除尚未被历史事实引用的来源渠道。 */
    @DeleteMapping("/source-channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteChannel(
            @PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        manager.deleteChannel(id, assembler.toDTO(r));
    }

    /**
     * 创建批次。
     */
    @PostMapping("/intake-batches")
    @ResponseStatus(HttpStatus.CREATED)
    IntakeBatchResponse createBatch(
            @Valid @RequestBody TalentIntakeBatchRequest r,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return manager.createBatch(assembler.toDTO(r), key);
    }

    @GetMapping("/intake-batches")
    CursorPage<IntakeBatchResponse> batches(
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return manager.batchResponses(cursor, limit);
    }

    @GetMapping("/intake-batches/{id}")
    IntakeBatchResponse batch(@PlatformIdentifier @PathVariable String id) {
        return manager.batchResponse(id);
    }

    /** 人工核对后，为缺少岗位上下文的历史收件批次补关联一个不可变 JD 版本。 */
    @PutMapping("/intake-batches/{id}/jd-version")
    IntakeBatchResponse linkBatchJdVersion(
            @PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody TalentIntakeBatchJdRequest r) {
        return assembler.toResponse(manager.linkBatchJdVersion(id, assembler.toDTO(r)));
    }

    @PostMapping(path = "/intake-batches/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ResumeIntakeResponse upload(
            @PlatformIdentifier @PathVariable String id,
            @ValidResumeFile @RequestPart("file") MultipartFile file,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return manager.uploadResponse(id, assembler.toDTO(file), key);
    }

    /**
     * 完成批次。
     */
    @PostMapping("/intake-batches/{id}/complete")
    IntakeBatchResponse completeBatch(@PlatformIdentifier @PathVariable String id) {
        return manager.completeBatchResponse(id);
    }

    @GetMapping("/intakes/count")
    CursorCount countIntakes(
            @OptionalPlatformIdentifier @RequestParam(required = false) String batchId,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @OptionalFilterCode @RequestParam(required = false) String importStatus,
            @RequestParam(required = false) Boolean duplicateContent,
            @RequestParam(required = false) @Size(max = MAX_CANDIDATE_QUERY_LENGTH) String query,
            @RequestParam(required = false) @Size(max = MAX_CANDIDATE_QUERY_LENGTH) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return manager.countIntakes(batchId, status, importStatus, duplicateContent, query, name, from, to);
    }

    @GetMapping("/intakes")
    CursorPage<ResumeIntakeResponse> intakes(
            @OptionalPlatformIdentifier @RequestParam(required = false) String batchId,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @OptionalFilterCode @RequestParam(required = false) String importStatus,
            @RequestParam(required = false) Boolean duplicateContent,
            @RequestParam(required = false) @Size(max = MAX_CANDIDATE_QUERY_LENGTH) String query,
            @RequestParam(required = false) @Size(max = MAX_CANDIDATE_QUERY_LENGTH) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return manager.intakeResponses(
                batchId, status, importStatus, duplicateContent, query, name, from, to, cursor, limit);
    }

    /** 用户明确启动后，后台解析并将简历加入待筛选库。 */
    @PostMapping("/intakes/{id}/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ResumeIntakeResponse importIntake(
            @PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody TalentIntakeImportRequest r) {
        return manager.importIntake(id, assembler.toDTO(r));
    }

    @PostMapping("/intakes/{id}/assign")
    ResumeIntakeResponse assign(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeAssignRequest r) {
        return manager.assign(id, assembler.toDTO(r));
    }

    @PostMapping("/intakes/{id}/confirm")
    ResumeIntakeResponse confirm(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeConfirmRequest r) {
        return manager.confirm(id, assembler.toDTO(r));
    }

    @PostMapping("/intakes/{id}/retry")
    ResumeIntakeResponse retry(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        return manager.retry(id, assembler.toDTO(r));
    }

    @PostMapping("/intakes/{id}/reparse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ResumeIntakeResponse reparse(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        return manager.reparse(id, assembler.toDTO(r));
    }

    @PostMapping("/intakes/{id}/ignore")
    ResumeIntakeResponse ignore(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        return manager.ignore(id, assembler.toDTO(r));
    }

    @GetMapping("/candidates")
    CursorPage<CandidateResponse> candidates(
            @RequestParam(required = false)
            @Size(max = MAX_CANDIDATE_QUERY_LENGTH, message = "query 长度不能超过 {max}")
            String query,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean poolOnly,
            @RequestParam(required = false) @Pattern(regexp = "PENDING_SCREENING|PENDING_DISPOSAL") String poolStage) {
        return manager.candidateResponses(query, cursor, limit, poolOnly,poolStage);
    }

    @GetMapping("/candidates/count")
    CursorCount countCandidates(
            @RequestParam(required = false)
            @Size(max = MAX_CANDIDATE_QUERY_LENGTH, message = "query 长度不能超过 {max}")
            String query,
            @ValidCursor @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean poolOnly,
            @RequestParam(required = false) @Pattern(regexp = "PENDING_SCREENING|PENDING_DISPOSAL") String poolStage) {
        return manager.countCandidates(query, cursor, poolOnly, poolStage);
    }

    @PostMapping("/intakes/{id}/score/retry")
    ResumeIntakeResponse retryScore(
            @PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        return manager.retryScore(id, assembler.toDTO(r));
    }

    @PostMapping("/intakes/{id}/score/recalculate")
    ResumeIntakeResponse recalculateScore(
            @PlatformIdentifier @PathVariable String id,
            @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        return manager.recalculateScore(id, assembler.toDTO(r));
    }

    @GetMapping("/candidates/{id}")
    CandidateResponse candidate(@PlatformIdentifier @PathVariable String id) {
        return manager.candidateResponse(id);
    }

    /**
     * 更新候选人。
     */
    @PatchMapping("/candidates/{id}")
    CandidateResponse updateCandidate(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeCandidateRequest r) {
        return manager.updateCandidate(id, assembler.toDTO(r));
    }

    /**
     * 删除候选人。
     */
    @DeleteMapping("/candidates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCandidate(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeVersionReasonRequest r) {
        manager.deleteCandidate(id, assembler.toDTO(r));
    }

    @GetMapping("/candidates/{id}/resume-versions")
    CursorPage<ResumeVersionResponse> resumeVersions(
            @PlatformIdentifier @PathVariable String id,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return manager.resumeVersionResponses(id, cursor, limit);
    }

    @GetMapping("/resume-versions/{id}/preview")
    ResumePreviewResponse preview(@PlatformIdentifier @PathVariable String id) {
        return manager.previewResponse(id);
    }

    /**
     * 校验并创建授权下载。
     */
    @PostMapping("/resume-versions/{id}/download-authorizations")
    DownloadGrantResponse authorizeDownload(@PlatformIdentifier @PathVariable String id) {
        return manager.authorizeDownloadResponse(id);
    }

    /** 按收件查看本次上传的原始简历，安全扫描与数据范围由后端逐次校验。 */
    @GetMapping("/intakes/{id}/file")
    ResponseEntity<InputStreamResource> intakeFile(@PlatformIdentifier @PathVariable String id) {
        DownloadedResumeResponse file = manager.downloadIntakeFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMediaType()))
                .contentLength(file.getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.getContent()));
    }

    @GetMapping("/interviews/{id}/materials")
    ResponseEntity<InterviewMaterialResponse> interviewMaterials(@PlatformIdentifier @PathVariable String id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(manager.interviewMaterials(id));
    }

    @GetMapping("/interviews/{id}/materials/resume")
    ResponseEntity<InputStreamResource> interviewResume(@PlatformIdentifier @PathVariable String id) {
        DownloadedResumeResponse file = manager.downloadInterviewResume(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMediaType()))
                .contentLength(file.getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.getContent()));
    }

    @GetMapping("/download-authorizations/{token}")
    ResponseEntity<InputStreamResource> download(
            @PathVariable
            @NotBlank(message = "下载令牌不能为空")
            @Size(
                    min = RESUME_DOWNLOAD_TOKEN_LENGTH,
                    max = RESUME_DOWNLOAD_TOKEN_LENGTH,
                    message = "下载令牌长度必须为 {min}")
            @Pattern(regexp = RESUME_DOWNLOAD_TOKEN_INPUT_PATTERN, message = "下载令牌格式无效")
            String token) {
        DownloadedResumeResponse file = manager.downloadResponse(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMediaType()))
                .contentLength(file.getSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new InputStreamResource(file.getContent()));
    }

    @GetMapping("/identity-conflicts")
    CursorPage<IdentityConflictResponse> conflicts(
            @OptionalPlatformIdentifier @RequestParam(required = false) String candidateId,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @RequestParam(required = false) @Size(max = 100) List<@PlatformIdentifier String> intakeIds,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return manager.conflictResponses(status, intakeIds, candidateId, cursor, limit);
    }

    /**
     * 解析并确定冲突。
     */
    @PostMapping("/identity-conflicts/{id}/resolve")
    IdentityConflictResponse resolveConflict(
            @PlatformIdentifier @PathVariable String id, @Valid @RequestBody TalentIntakeConflictRequest r) {
        return manager.resolveConflict(id, assembler.toDTO(r));
    }
}
