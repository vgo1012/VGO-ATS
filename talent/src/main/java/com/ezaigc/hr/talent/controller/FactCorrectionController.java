package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.domain.dto.CorrectResumeFactsDTO;
import com.ezaigc.hr.talent.domain.request.FactCorrectionRequest;
import com.ezaigc.hr.talent.domain.response.FactCorrectionResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fact-corrections")
public class FactCorrectionController {

  @Resource private ResumeParsingApplicationManager service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  FactCorrectionResponse correct(@Valid @RequestBody FactCorrectionRequest request) {
    return service.correct(
        new CorrectResumeFactsDTO(
            request.getResumeVersionId(),
            request.getBaseFactSnapshotId(),
            request.getReason(),
            request.getFacts()));
  }
}
