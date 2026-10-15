package com.ezaigc.hr.identity.config;

import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 下次登录是否必须修改密码请求或数据过滤器。 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED_PATHS =
      /** 根据给定值创建当前对象。 */
      Set.of("/api/v1/me", "/api/v1/me/password", "/api/v1/auth/logout", "/api/v1/auth/csrf");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof IdentityPrincipal principal
        && principal.isMustChangePassword()
        && !ALLOWED_PATHS.contains(request.getRequestURI())) {
      SecurityProblemWriter.write(response, 403, "PASSWORD_CHANGE_REQUIRED", "首次登录或凭据重置后必须先修改密码");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
