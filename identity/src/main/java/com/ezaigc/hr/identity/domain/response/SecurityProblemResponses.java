package com.ezaigc.hr.identity.domain.response;

import com.ezaigc.hr.identity.config.SecurityProblemWriter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;

public final class SecurityProblemResponses {

  private SecurityProblemResponses() {}

  public static void unauthorized(HttpServletResponse response) throws IOException {
    SecurityProblemWriter.write(response, 401, "SESSION_REQUIRED", "需要有效登录会话");
  }

  public static void forbidden(HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    if (exception instanceof CsrfException) {
      SecurityProblemWriter.write(response, 403, "CSRF_TOKEN_INVALID", "CSRF 令牌缺失或无效");
      return;
    }
    SecurityProblemWriter.write(response, 403, "ACCESS_DENIED", "当前账号无权执行此操作");
  }
}
