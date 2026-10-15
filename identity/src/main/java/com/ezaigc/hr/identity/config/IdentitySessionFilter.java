package com.ezaigc.hr.identity.config;

import com.ezaigc.hr.identity.application.manager.IdentitySessionApplicationManager;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 仅由安全链注册，避免作为 Servlet Filter 重复执行。 */
public class IdentitySessionFilter extends OncePerRequestFilter {

  private final IdentitySessionApplicationManager sessions;

  public IdentitySessionFilter(IdentitySessionApplicationManager sessions) {
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof IdentityPrincipal principal
        && !sessions.isCurrentSession(principal)) {
      SecurityContextHolder.clearContext();
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
    }
    // 由原授权链拒绝受保护请求；登录和 CSRF 入口仍可匿名继续。
    filterChain.doFilter(request, response);
  }
}
