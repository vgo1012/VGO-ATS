package com.ezaigc.hr.recruiting.activity.controller;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityRecordManager;
import com.ezaigc.hr.recruiting.activity.domain.response.ActivityRecordResponse;
import jakarta.annotation.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivityRecordController {
  @Resource private ActivityRecordManager manager;
  @GetMapping("/api/v1/applications/{applicationId}/activity-records")
  public ResponseEntity<CursorPage<ActivityRecordResponse>> records(
      @PlatformIdentifier @PathVariable String applicationId,
      @OptionalFilterCode @RequestParam(required = false) String type,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(manager.records(applicationId, type, cursor, limit));
  }
}
