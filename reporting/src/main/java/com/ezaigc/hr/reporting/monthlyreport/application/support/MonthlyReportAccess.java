package com.ezaigc.hr.reporting.monthlyreport.application.support;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** 所有月报入口和通用任务投影共享同一角色边界。 */
@Component
public class MonthlyReportAccess {
    @Resource private AuthorizationService authorization;
    public CurrentActorSnapshot require() {
        CurrentActorSnapshot actor = authorization.currentActor();
        if (!allowed(actor)) throw PlatformExceptions.forbidden("MONTHLY_REPORT_FORBIDDEN", "仅人力负责人和管理员可以使用招聘月度汇报");
        return actor;
    }
    public static boolean allowed(CurrentActorSnapshot actor) {
        return actor != null && (actor.isSuperAdmin() || (actor.getRoles() != null
                && actor.getRoles().contains("HR_DIRECTOR")
                && actor.hasPermission(MonthlyReportConstants.PERMISSION)));
    }
}
