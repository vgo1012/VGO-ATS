package com.ezaigc.hr.intelligence.matching.application.adapter;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.api.ActiveMatchPlanQueryService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveMatchPlanQueryAdapter implements ActiveMatchPlanQueryService {

    @Resource
    private MatchPlanVersionPersistenceService matchPlanVersionPersistenceService;

    @Override
    @Transactional(readOnly = true)
    public ActiveMatchPlanSnapshot requireActive(String jdVersionId) {
        var plan = matchPlanVersionPersistenceService.findActiveMatchPlanVersion(jdVersionId);
        if (plan == null) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "ACTIVE_MATCH_PLAN_REQUIRED", "JD 尚未激活正式评分配置", false);
        }
        return new ActiveMatchPlanSnapshot(
                plan.getId(), jdVersionId, plan.getControlledCriterionPlanVersionId(), plan.getVersionNo());
    }
}
