package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.talent.application.manager.CandidateBatchApplicationManager;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CandidateBatchController {
  @Resource private CandidateBatchApplicationManager manager;

  /** 仅返回仍可读取的候选人，去重后保持请求顺序；无权、删除和不存在的项省略。 */
  @GetMapping("/api/v1/candidate-batch")
  public ResponseEntity<List<CandidateResponse>> read(
      @RequestParam @Size(max = 100) List<@PlatformIdentifier String> candidateIds) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(manager.read(candidateIds));
  }
}
