package com.ezaigc.hr.identity.controller;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.application.manager.IdentityAdministrationApplicationManager;
import com.ezaigc.hr.identity.application.manager.IdentityAuthenticationApplicationManager;
import com.ezaigc.hr.identity.application.manager.IdentitySessionApplicationManager;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.request.AuthChangePasswordRequest;
import com.ezaigc.hr.identity.domain.request.AuthLoginRequest;
import com.ezaigc.hr.identity.domain.response.AuthCsrfResponse;
import com.ezaigc.hr.identity.domain.response.AuthMeResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    @Resource
    private IdentityAdministrationApplicationManager administrationService;

    @Resource
    private IdentityAuthenticationApplicationManager authenticationApplicationManager;
    @Resource
    private IdentitySessionApplicationManager sessionApplicationManager;
    @Resource
    private SecurityContextRepository securityContextRepository;

    @GetMapping("/auth/csrf")
    AuthCsrfResponse csrf(CsrfToken csrfToken) {
        return new AuthCsrfResponse(
                csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/auth/login")
    AuthMeResponse login(
            @Valid @RequestBody AuthLoginRequest command,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication =
                authenticationApplicationManager.authenticate(
                        command.getWorkEmail(), command.getPassword());
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        IdentityPrincipal principal = (IdentityPrincipal) authentication.getPrincipal();
        return AuthMeResponse.from(principal, sessionApplicationManager.supportDepartmentIds(principal.getUserId()));
    }

    @PostMapping("/auth/logout")
    void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextRepository.saveContext(
                SecurityContextHolder.createEmptyContext(), request, response);
    }

    @GetMapping("/me")
    AuthMeResponse me(@AuthenticationPrincipal IdentityPrincipal principal) {
        if (principal == null) {
            throw new PlatformException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "登录会话已失效", true);
        }
        return AuthMeResponse.from(principal, sessionApplicationManager.supportDepartmentIds(principal.getUserId()));
    }

    /**
     * 变更密码。
     */
    @PostMapping("/me/password")
    void changePassword(
            @Valid @RequestBody AuthChangePasswordRequest command, HttpServletRequest request) {
        administrationService.changeOwnPassword(command.getCurrentPassword(), command.getNewPassword());
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
