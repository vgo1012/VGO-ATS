package com.ezaigc.hr.identity.controller;

import com.ezaigc.hr.identity.application.manager.WecomDirectoryApplicationManager;
import com.ezaigc.hr.identity.application.manager.WecomSyncApplicationManager;
import com.ezaigc.hr.identity.application.manager.WecomAccountLinkApplicationManager;
import com.ezaigc.hr.identity.domain.request.IdentityAdminReasonRequest;
import com.ezaigc.hr.identity.domain.request.WecomAccountLinkRequest;
import com.ezaigc.hr.identity.domain.request.WecomDepartmentLinkRequest;
import com.ezaigc.hr.identity.domain.request.WecomSyncRequest;
import com.ezaigc.hr.identity.domain.response.WecomSyncBatchResponse;
import com.ezaigc.hr.identity.domain.response.WecomStoredDirectoryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.ezaigc.hr.identity.domain.response.WecomDirectoryPreviewResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wecom/directory")
public class WecomDirectoryController {
    @Resource private WecomDirectoryApplicationManager manager;
    @Resource private WecomSyncApplicationManager sync;
    @Resource private WecomAccountLinkApplicationManager accountLinks;

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WecomSyncBatchResponse synchronize(@Valid @RequestBody WecomSyncRequest request) {
        return sync.synchronize(request);
    }

    @GetMapping("/batches")
    public List<WecomSyncBatchResponse> batches(@RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page) {
        return sync.history(page);
    }

    @GetMapping("/batches/{id}")
    public WecomSyncBatchResponse batch(@PathVariable @PlatformIdentifier String id) {
        return sync.batch(id);
    }

    @GetMapping("/stored")
    public WecomStoredDirectoryResponse stored() {
        return sync.stored();
    }

    @PostMapping("/members/{memberId}/account-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkAccount(@PathVariable @PlatformIdentifier String memberId,
            @Valid @RequestBody WecomAccountLinkRequest request) {
        accountLinks.link(memberId, request.getUserAccountId(), request.getReason());
    }

    @DeleteMapping("/members/{memberId}/account-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkAccount(@PathVariable @PlatformIdentifier String memberId,
            @Valid @RequestBody IdentityAdminReasonRequest request) {
        accountLinks.unlink(memberId, request.getReason());
    }

    @PostMapping("/departments/{departmentId}/local-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkDepartment(@PathVariable @PlatformIdentifier String departmentId,
            @Valid @RequestBody WecomDepartmentLinkRequest request) {
        accountLinks.linkDepartment(departmentId, request.getLocalDepartmentId(), request.getReason());
    }

    @DeleteMapping("/departments/{departmentId}/local-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkDepartment(@PathVariable @PlatformIdentifier String departmentId,
            @Valid @RequestBody IdentityAdminReasonRequest request) {
        accountLinks.unlinkDepartment(departmentId, request.getReason());
    }

    @GetMapping("/status")
    public WecomDirectoryPreviewResponse status() {
        return manager.status();
    }

    @PostMapping("/preview")
    public WecomDirectoryPreviewResponse preview() {
        return manager.preview();
    }
}
