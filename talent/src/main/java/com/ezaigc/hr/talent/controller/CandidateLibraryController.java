package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.application.manager.CandidateLibraryApplicationManager;
import com.ezaigc.hr.talent.application.manager.CandidateRescoreApplicationManager;
import com.ezaigc.hr.talent.domain.request.TalentIntakeVersionReasonRequest;
import com.ezaigc.hr.talent.domain.request.CandidateRescoreRequest;
import com.ezaigc.hr.talent.domain.request.SharedCandidateReclaimRequest;
import com.ezaigc.hr.talent.domain.response.CandidateRescoreResponse;
import com.ezaigc.hr.talent.domain.response.CandidatePoolStatusResponse;
import com.ezaigc.hr.talent.domain.response.SharedCandidateReclaimResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CandidateLibraryController {
    @Resource private CandidateLibraryApplicationManager manager;
    @Resource private CandidateRescoreApplicationManager rescoreManager;
    @Resource private TalentIntakeAssembler assembler;
    @GetMapping("/candidate-pool-statuses")
    List<CandidatePoolStatusResponse> statuses(@RequestParam @Size(max=100) List<@PlatformIdentifier String> candidateIds) {
        return manager.statuses(candidateIds);
    }
    @PostMapping("/candidates/{id}/screen-out")
    CandidatePoolStatusResponse screenOut(@PathVariable @PlatformIdentifier String id,
            @RequestBody @Valid TalentIntakeVersionReasonRequest request) {
        return manager.screenOut(id, assembler.toDTO(request));
    }

    @PostMapping("/shared-candidate-reclaims")
    SharedCandidateReclaimResponse reclaim(
            @RequestBody @Valid SharedCandidateReclaimRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return manager.reclaim(request, idempotencyKey);
    }

    @PostMapping("/shared-candidate-reclaim-scores")
    CandidateRescoreResponse rescore(
            @RequestBody @Valid CandidateRescoreRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return rescoreManager.rescore(request, idempotencyKey);
    }
}
