package com.ezaigc.hr.recruiting.recruitment.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ExtendedPageLimit;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService.LockSummary;
import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CollaborationRequestResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockPolicyHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockPolicyResponse;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EngagementLockController {

    @Resource
    private EngagementLockApplicationManager manager;

    @GetMapping("/candidates/{candidateId}/engagement-lock")
    LockSummary active(@PlatformIdentifier @PathVariable String candidateId) {
        return manager.activeLock(candidateId);
    }

    @GetMapping("/candidates/{candidateId}/engagement-collaboration-requests")
    List<CollaborationRequestResponse> collaborations(
            @PlatformIdentifier @PathVariable String candidateId,
            @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
        return manager.collaborations(candidateId, limit);
    }

    @GetMapping("/candidates/{candidateId}/engagement-lock-history")
    List<LockHistoryResponse> history(
            @PlatformIdentifier @PathVariable String candidateId,
            @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
        return manager.history(candidateId, limit);
    }

    @GetMapping("/admin/engagement-lock-policy")
    LockPolicyResponse policy() {
        return manager.policy();
    }

    @GetMapping("/admin/engagement-lock-policy/history")
    List<LockPolicyHistoryResponse> policyHistory(
            @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
        return manager.policyHistory(limit);
    }
}
