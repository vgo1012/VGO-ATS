package com.ezaigc.hr.identity.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.domain.entity.WecomAccountLinkEntity;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.domain.response.WecomCollaborationMemberResponse;
import com.ezaigc.hr.identity.enums.WecomDirectoryVisibility;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WecomCollaborationApplicationManager {
    private static final int ACTIVE_MEMBER_STATUS = 1;

    @Resource private AuthorizationService authorization;
    @Resource private WecomMemberPersistenceService members;
    @Resource private WecomAccountLinkPersistenceService links;

    public List<WecomCollaborationMemberResponse> search(String query, int limit) {
        authorization.requirePermission("interview.schedule");
        int bounded = Math.clamp(limit, 1, 50);
        String normalized = query == null ? null : query.trim();
        List<WecomMemberEntity> values = members.page(new Page<>(1, bounded, false),
                Wrappers.lambdaQuery(WecomMemberEntity.class)
                .eq(WecomMemberEntity::getDirectoryStatus, WecomDirectoryVisibility.VISIBLE.name())
                .eq(WecomMemberEntity::getSourceStatus, ACTIVE_MEMBER_STATUS)
                .and(normalized != null && !normalized.isEmpty(), value -> value
                        .like(WecomMemberEntity::getName, normalized)
                        .or().like(WecomMemberEntity::getUserId, normalized)
                        .or().like(WecomMemberEntity::getPosition, normalized))
                .orderByAsc(WecomMemberEntity::getName, WecomMemberEntity::getUserId))
                .getRecords();
        if (values.isEmpty()) return List.of();
        Map<String, String> accountsByMember = links.list(Wrappers.lambdaQuery(WecomAccountLinkEntity.class)
                        .in(WecomAccountLinkEntity::getWecomMemberId,
                                values.stream().map(WecomMemberEntity::getId).toList()))
                .stream().collect(Collectors.toMap(WecomAccountLinkEntity::getWecomMemberId,
                        WecomAccountLinkEntity::getUserAccountId, (left, right) -> right));
        return values.stream()
                .map(value -> new WecomCollaborationMemberResponse(
                        value.getUserId(), value.getName(), value.getPosition(), accountsByMember.get(value.getId())))
                .toList();
    }
}
