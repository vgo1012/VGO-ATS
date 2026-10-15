package com.ezaigc.hr.identity.controller;

import com.ezaigc.hr.identity.application.manager.WecomCollaborationApplicationManager;
import com.ezaigc.hr.identity.domain.response.WecomCollaborationMemberResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wecom/collaboration")
public class WecomCollaborationController {
    @Resource private WecomCollaborationApplicationManager manager;

    @GetMapping("/members")
    public List<WecomCollaborationMemberResponse> members(
            @Size(max = 100) @RequestParam(required = false) String query,
            @Min(1) @Max(50) @RequestParam(defaultValue = "20") int limit) {
        return manager.search(query, limit);
    }
}
