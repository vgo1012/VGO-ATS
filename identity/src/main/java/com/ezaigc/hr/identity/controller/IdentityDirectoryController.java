package com.ezaigc.hr.identity.controller;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.identity.application.manager.IdentityDirectoryApplicationManager;
import com.ezaigc.hr.identity.domain.response.DirectoryUserResponse;
import com.ezaigc.hr.identity.domain.response.DirectoryDepartmentResponse;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/directory")
public class IdentityDirectoryController {
  @Resource private IdentityDirectoryApplicationManager manager;

  @GetMapping("/users")
  CursorPage<DirectoryUserResponse> users(
      @Size(max = 100) @RequestParam(required = false) String query,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(required = false) Integer limit) {
    return manager.users(query, cursor, limit);
  }

  @GetMapping("/users/resolve")
  List<DirectoryUserResponse> resolve(
      @Size(min = 1, max = 100) @RequestParam Set<@PlatformIdentifier String> ids) {
    return manager.resolve(ids);
  }

  @GetMapping("/departments")
  List<DirectoryDepartmentResponse> departments() {
    return manager.departments();
  }
}
