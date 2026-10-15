package com.ezaigc.hr.reporting.controller;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.reporting.application.manager.TaskCenterApplicationManager;
import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import com.ezaigc.hr.reporting.domain.response.TaskCenterOverviewResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/task-center")
public class TaskCenterController {
    @Resource private TaskCenterApplicationManager manager;

    @GetMapping
    public TaskCenterOverviewResponse overview() { return manager.overview(); }

    @GetMapping("/history")
    public CursorPage<AsyncTaskResponse> history(
            @OptionalFilterCode @RequestParam String jobCode,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return manager.history(jobCode, cursor, limit);
    }
}
