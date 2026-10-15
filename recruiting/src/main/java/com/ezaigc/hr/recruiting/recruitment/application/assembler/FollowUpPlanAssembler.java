package com.ezaigc.hr.recruiting.recruitment.application.assembler;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;
import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.FollowUpPlanDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.request.FollowUpPlanRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanSnapshotResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
@Component
public class FollowUpPlanAssembler {
  private static final ObjectMapper JSON = new ObjectMapper();
  public FollowUpPlanHistoryResponse toHistoryResponse(FollowUpPlanHistory history) {
    return new FollowUpPlanHistoryResponse(history.getId(), history.getPlanId(), history.getAction(), history.getReason(),
        history.getCreateId(), history.getCreateTime(), snapshot(history.getBeforeSnapshot()), snapshot(history.getAfterSnapshot()));
  }
  private FollowUpPlanSnapshotResponse snapshot(String value) {
    if (value == null) return null;
    var plan = JSON.readValue(value, FollowUpPlan.class);
    return new FollowUpPlanSnapshotResponse(plan.getStartsAt(), plan.getEndsAt(), plan.getChannel(), plan.getNote(),
        plan.getStatus(), plan.getResultReason());
  }
  public FollowUpPlanDTO toDTO(FollowUpPlanRequest value) {
    return new FollowUpPlanDTO(value.getStartsAt(), value.getEndsAt(), value.getChannel(), value.getNote(), parse(value.getVersion()));
  }
  public FollowUpPlanResponse toResponse(FollowUpPlan plan, Application app, JdVersionSnapshot jd) {
    return toResponse(plan, app, new JdClientSummary(jd.getClientId(), jd.getClientName()));
  }
  public FollowUpPlanResponse toResponse(FollowUpPlan plan, Application app, JdClientSummary jd) {
    return new FollowUpPlanResponse(plan.getId(), app.getId(), app.getCandidateId(), app.getJdId(),
        app.getJdVersionId(), app.getRecruitmentType(), jd.getClientId(), jd.getClientName(), app.getOwnerUserId(),
        plan.getStartsAt(), plan.getEndsAt(), plan.getChannel(), plan.getNote(), plan.getStatus(), plan.getResultReason(),
        format(plan.getVersion()), plan.getCreateTime(), plan.getUpdateTime());
  }
}
