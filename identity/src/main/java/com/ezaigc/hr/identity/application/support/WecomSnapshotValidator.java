package com.ezaigc.hr.identity.application.support;

import com.ezaigc.hr.identity.api.WecomDepartmentDTO;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.api.WecomMemberDTO;
import com.ezaigc.hr.identity.enums.WecomDirectoryReadStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WecomSnapshotValidator {
    private WecomSnapshotValidator() {}

    public static void validate(WecomDirectoryResultDTO snapshot, String corpId) {
        if (!WecomDirectoryReadStatus.PREVIEW_READY.matches(snapshot.getStatus()) || !corpId.equals(snapshot.getCorpId())
                || snapshot.getDepartments().isEmpty() || snapshot.getMembers().isEmpty()) {
            reject();
        }
        Map<Long, WecomDepartmentDTO> departments = new HashMap<>();
        for (WecomDepartmentDTO department : snapshot.getDepartments()) {
            text(department.getName(), 255, true);
            if (department.getDepartmentId() <= 0 || department.getParentId() < 0
                    || departments.put(department.getDepartmentId(), department) != null) reject();
        }
        for (WecomDepartmentDTO department : departments.values()) {
            if (department.isParentOutsideScope() != (department.getParentId() != 0
                    && !departments.containsKey(department.getParentId()))) reject();
            Set<Long> visited = new HashSet<>();
            long cursor = department.getDepartmentId();
            while (departments.containsKey(cursor)) {
                if (!visited.add(cursor)) reject();
                cursor = departments.get(cursor).getParentId();
            }
        }
        Set<String> memberIds = new HashSet<>();
        for (WecomMemberDTO member : snapshot.getMembers()) {
            text(member.getUserId(), 128, true);
            text(member.getName(), 255, true);
            text(member.getPosition(), 255, false);
            text(member.getBusinessEmail(), 320, false);
            if (!memberIds.add(member.getUserId()) || member.getDepartmentIds().isEmpty()
                    || new HashSet<>(member.getDepartmentIds()).size() != member.getDepartmentIds().size()
                    || member.getDepartmentIds().stream().anyMatch(id -> id == null || id <= 0)
                    || member.getDepartmentIds().stream().noneMatch(departments::containsKey)) reject();
            List<String> direct = member.getDirectLeaderUserIds();
            if (direct != null) {
                for (String leader : direct) text(leader, 128, true);
                if (new HashSet<>(direct).size() != direct.size() || direct.contains(member.getUserId())) reject();
            }
        }
    }

    private static void text(String value, int maximum, boolean required) {
        if ((required && (value == null || value.isBlank())) || (value != null && value.length() > maximum)) reject();
    }

    private static void reject() {
        throw new WecomSnapshotRejectedException("INVALID_SNAPSHOT", "目录为空或身份、字段、关系不完整；保留上次成功目录");
    }
}
