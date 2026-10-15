package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 按页读取候选人基本资料，沿用单人详情的读取权限，不装载简历全文。 */
@Component
public class CandidateBatchApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private CandidatePersistenceService candidates;
  @Resource private CandidateReadAccess access;
  @Resource private TalentIntakeAssembler assembler;

  @Transactional(readOnly = true)
  public List<CandidateResponse> read(List<String> candidateIds) {
    authorization.currentActor();
    if (candidateIds == null || candidateIds.size() > 100) {
      throw PlatformExceptions.unprocessable("TOO_MANY_CANDIDATES", "候选人资料每次最多100项");
    }
    var ids = candidateIds.stream().map(id -> RequiredIdentifier.require(id, "candidateId")).distinct().toList();
    if (ids.isEmpty()) return List.of();
    var rows = candidates.listByIds(ids).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus())).toList();
    var decision = access.evaluate(rows);
    var readable = rows.stream().filter(row -> decision.canReadCandidate(row.getId()))
        .collect(Collectors.toMap(Candidate::getId, Function.identity()));
    return ids.stream().filter(readable::containsKey).map(readable::get).map(assembler::toResponse).toList();
  }
}
