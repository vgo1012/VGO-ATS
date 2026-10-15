package com.ezaigc.hr.identity.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverItemRecord;
import com.ezaigc.hr.identity.domain.response.AdminUserResponse;
import com.ezaigc.hr.identity.domain.response.DepartmentResponse;
import com.ezaigc.hr.identity.domain.response.HandoverBatchDetailsResponse;
import com.ezaigc.hr.identity.domain.response.HandoverBatchResponse;
import com.ezaigc.hr.identity.domain.response.HandoverItemResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 身份管理边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。
 */
@Component
public class IdentityAdministrationAssembler {

    public DepartmentResponse toResponse(DepartmentRecord v) {
        return new DepartmentResponse(
                v.getId(),
                v.getCode(),
                v.getName(),
                v.getParentDepartmentId(),
                v.getAssignmentQueueOwnerUserId(),
                v.getAssignmentQueueOwnerName(),
                v.getStatus(),
                format(v.getVersion()));
    }

    public List<DepartmentResponse> toDepartmentResponses(List<DepartmentRecord> values) {
        return values.stream().map(this::toResponse).toList();
    }

    public AdminUserResponse toResponse(AdminUserRecord v) {
        return new AdminUserResponse(
                v.getId(),
                v.getDisplayName(),
                v.getWorkEmail(),
                v.getSourceTitle(),
                v.getHomeDepartmentId(),
                v.getHomeDepartmentName(),
                v.getDirectLeaderUserId(),
                v.getDirectLeaderName(),
                v.getStatus(),
                v.isMustChangePassword(),
                format(v.getVersion()));
    }

    public List<AdminUserResponse> toUserResponses(List<AdminUserRecord> values) {
        return values.stream().map(this::toResponse).toList();
    }

    public HandoverBatchResponse toResponse(HandoverBatchRecord v) {
        return new HandoverBatchResponse(
                v.getId(),
                v.getSourceUserId(),
                v.getTargetUserId(),
                v.getSelectionMode(),
                v.getStatus(),
                v.getTotalCount(),
                v.getSucceededCount(),
                v.getFailedCount(),
                v.getUnresolvedCount(),
                v.getReason(),
                v.getRequestedByUserId(),
                v.getStartedAt(),
                v.getCompletedAt(),
                format(v.getVersion()));
    }

    public List<HandoverBatchResponse> toBatchResponses(List<HandoverBatchRecord> values) {
        return values.stream().map(this::toResponse).toList();
    }

    public HandoverItemResponse toResponse(HandoverItemRecord v) {
        return new HandoverItemResponse(
                v.getId(),
                v.getBatchId(),
                v.getResourceType(),
                v.getResourceId(),
                v.getStatus(),
                v.isMandatory(),
                v.getErrorCode(),
                v.getErrorMessage(),
                v.getFromOwnerUserId(),
                v.getToOwnerUserId(),
                v.getOwnershipHistoryId());
    }

    public HandoverBatchDetailsResponse toDetails(
            HandoverBatchRecord batch, List<HandoverItemRecord> items) {
        return new HandoverBatchDetailsResponse(
                toResponse(batch), items.stream().map(this::toResponse).toList());
    }
}
