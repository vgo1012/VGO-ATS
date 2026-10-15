package com.ezaigc.hr.intelligence.search.controller;

import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.intelligence.search.application.assembler.TalentSearchAssembler;
import com.ezaigc.hr.intelligence.search.application.manager.TalentSearchApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.request.TalentSearchRequest;
import com.ezaigc.hr.intelligence.search.domain.response.TalentSearchPageResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TalentSearchController {
  @Resource private TalentSearchAssembler assembler;
  @Resource private TalentSearchApplicationManager manager;

  @GetMapping("/talent-search")
  TalentSearchPageResponse search(@Valid @ModelAttribute TalentSearchRequest request) {
    return manager.search(assembler.toDTO(request), request.getCursor(), request.getLimit());
  }

  @GetMapping("/talent-search/count")
  CursorCount count(@Valid @ModelAttribute TalentSearchRequest request) {
    return manager.count(assembler.toDTO(request), request.getCursor());
  }
}
