package com.ezaigc.hr.identity.application.assembler;

import com.ezaigc.hr.identity.domain.entity.WecomDepartmentEntity;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.domain.response.WecomStoredDepartmentResponse;
import com.ezaigc.hr.identity.domain.response.WecomStoredMemberResponse;
import com.ezaigc.hr.identity.domain.response.WecomSyncBatchResponse;
import jakarta.annotation.Resource;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class WecomSyncAssembler {
    @Resource private ObjectMapper json;

    public WecomSyncBatchResponse batch(WecomSyncBatchEntity batch) {
        return new WecomSyncBatchResponse(batch.getId(), batch.getCorpId(), batch.getTriggerType(),
                batch.getActorUserId(), batch.getRetryOfBatchId(), batch.getScopeVersion(), batch.getStatus(),
                batch.getRequestedAt(), batch.getStartedAt(), batch.getFinishedAt(), batch.getLeaseUntil(),
                batch.getErrorCode(), batch.getSafeMessage(), batch.getDepartmentCount(), batch.getMemberCount(),
                batch.getInsertedCount(), batch.getChangedCount(), batch.getMissingCount());
    }

    public WecomStoredDepartmentResponse department(WecomDepartmentEntity department, DepartmentRecord linkedDepartment) {
        return new WecomStoredDepartmentResponse(department.getId(), department.getDepartmentId(),
                department.getParentId(), department.getName(), department.getSourceOrder(),
                department.isParentOutsideScope(), department.getChangedBatchId(),
                linkedDepartment == null ? null : linkedDepartment.getId(),
                linkedDepartment == null ? null : linkedDepartment.getName());
    }

    public WecomStoredMemberResponse member(WecomMemberEntity member, AdminUserRecord linkedUser) {
        return new WecomStoredMemberResponse(member.getId(), member.getUserId(), member.getName(),
                member.getPosition(), member.getBusinessEmail(), member.getSourceStatus(),
                Arrays.asList(json.readValue(member.getDepartmentIdsJson(), Long[].class)),
                member.getDirectLeaderIdsJson() == null ? null
                        : Arrays.asList(json.readValue(member.getDirectLeaderIdsJson(), String[].class)),
                member.getDirectoryStatus(), member.getChangedBatchId(),
                linkedUser == null ? null : linkedUser.getId(),
                linkedUser == null ? null : linkedUser.getDisplayName(),
                linkedUser == null ? null : linkedUser.getWorkEmail());
    }
}
