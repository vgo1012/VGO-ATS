package com.ezaigc.hr.identity.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.application.assembler.WecomDirectoryAssembler;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import com.ezaigc.hr.identity.domain.response.WecomDirectoryPreviewResponse;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class WecomDirectoryApplicationManager {
    @Resource private AuthorizationService authorization;
    @Resource private WecomDirectoryPort directory;
    @Resource private WecomDirectoryAssembler assembler;

    public WecomDirectoryPreviewResponse status() {
        requireSuperAdmin();
        return assembler.toResponse(directory.status());
    }

    public WecomDirectoryPreviewResponse preview() {
        requireSuperAdmin();
        return assembler.toResponse(directory.preview());
    }

    private void requireSuperAdmin() {
        if (!authorization.currentActor().isSuperAdmin()) {
            throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
        }
    }
}
