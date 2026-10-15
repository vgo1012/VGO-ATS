package com.ezaigc.hr.identity.application.assembler;

import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.domain.response.WecomDepartmentResponse;
import com.ezaigc.hr.identity.domain.response.WecomDirectoryPreviewResponse;
import com.ezaigc.hr.identity.domain.response.WecomMemberResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WecomDirectoryAssembler {
    public WecomDirectoryPreviewResponse toResponse(WecomDirectoryResultDTO result) {
        return new WecomDirectoryPreviewResponse(result.getStatus(), result.getMessage(),
                result.getCorpId(), result.getObservedAt(), result.getRetryAfterSeconds(),
                result.getDepartments().stream().map(department -> new WecomDepartmentResponse(
                        department.getDepartmentId(), department.getParentId(), department.getName(),
                        department.getOrder(), department.isParentOutsideScope())).toList(),
                result.getMembers().stream().map(member -> new WecomMemberResponse(
                        member.getUserId(), member.getName(), member.getPosition(),
                        member.getBusinessEmail(), member.getSourceStatus(),
                        List.copyOf(member.getDepartmentIds()), member.getDirectLeaderUserIds())).toList());
    }
}
