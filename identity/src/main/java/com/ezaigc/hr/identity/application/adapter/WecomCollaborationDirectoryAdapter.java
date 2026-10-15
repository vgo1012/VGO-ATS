package com.ezaigc.hr.identity.application.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.WecomCollaborationDirectoryService;
import com.ezaigc.hr.identity.domain.entity.WecomAccountLinkEntity;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.enums.WecomDirectoryVisibility;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WecomCollaborationDirectoryAdapter implements WecomCollaborationDirectoryService {
    private static final int ACTIVE_MEMBER_STATUS = 1;

    @Resource private WecomMemberPersistenceService members;
    @Resource private WecomAccountLinkPersistenceService links;

    @Override
    public Map<String, String> requireLinkedUserIds(Set<String> localUserIds) {
        Set<String> normalized = normalized(localUserIds, "本地面试官集合不能为空");
        if (normalized.isEmpty()) return Map.of();
        List<WecomAccountLinkEntity> linked = links.list(Wrappers.lambdaQuery(WecomAccountLinkEntity.class)
                .in(WecomAccountLinkEntity::getUserAccountId, normalized));
        Map<String, WecomAccountLinkEntity> byAccount = new LinkedHashMap<>();
        linked.forEach(value -> byAccount.put(value.getUserAccountId(), value));
        if (!byAccount.keySet().containsAll(normalized)) {
            throw PlatformExceptions.unprocessable(
                    "WECOM_ACCOUNT_LINK_REQUIRED", "所选面试官中有账号尚未关联企业微信成员，请先完成账号关联");
        }
        Map<String, WecomMemberEntity> byId = activeMembers(linked.stream()
                .map(WecomAccountLinkEntity::getWecomMemberId).collect(Collectors.toSet()));
        Map<String, String> result = new LinkedHashMap<>();
        for (String accountId : normalized) {
            WecomMemberEntity member = byId.get(byAccount.get(accountId).getWecomMemberId());
            if (member == null) {
                throw PlatformExceptions.unprocessable(
                        "WECOM_MEMBER_NOT_AVAILABLE", "所选面试官关联的企业微信成员当前不可用，请同步目录后重试");
            }
            result.put(accountId, member.getUserId());
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<String, Member> requireMembers(Set<String> sourceUserIds) {
        Set<String> normalized = normalized(sourceUserIds, "企业微信成员集合不能为空");
        if (normalized.isEmpty()) return Map.of();
        List<WecomMemberEntity> values = members.list(Wrappers.lambdaQuery(WecomMemberEntity.class)
                .in(WecomMemberEntity::getUserId, normalized)
                .eq(WecomMemberEntity::getDirectoryStatus, WecomDirectoryVisibility.VISIBLE.name())
                .eq(WecomMemberEntity::getSourceStatus, ACTIVE_MEMBER_STATUS));
        Map<String, WecomMemberEntity> byUserId = new LinkedHashMap<>();
        values.forEach(value -> byUserId.put(value.getUserId(), value));
        if (!byUserId.keySet().containsAll(normalized)) {
            throw PlatformExceptions.unprocessable(
                    "WECOM_MEMBER_NOT_AVAILABLE", "所选企业微信成员不存在、未激活或已不在应用可见范围");
        }
        Map<String, String> accountsByMember = linkedAccounts(values.stream()
                .map(WecomMemberEntity::getId).collect(Collectors.toSet()));
        Map<String, Member> result = new LinkedHashMap<>();
        for (String userId : normalized) {
            WecomMemberEntity value = byUserId.get(userId);
            result.put(userId, view(value, accountsByMember.get(value.getId())));
        }
        return Map.copyOf(result);
    }

    private Map<String, WecomMemberEntity> activeMembers(Set<String> memberIds) {
        Map<String, WecomMemberEntity> result = new LinkedHashMap<>();
        members.list(Wrappers.lambdaQuery(WecomMemberEntity.class)
                        .in(WecomMemberEntity::getId, memberIds)
                        .eq(WecomMemberEntity::getDirectoryStatus, WecomDirectoryVisibility.VISIBLE.name())
                        .eq(WecomMemberEntity::getSourceStatus, ACTIVE_MEMBER_STATUS))
                .forEach(value -> result.put(value.getId(), value));
        return result;
    }

    private Map<String, String> linkedAccounts(Set<String> memberIds) {
        if (memberIds.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        links.list(Wrappers.lambdaQuery(WecomAccountLinkEntity.class)
                        .in(WecomAccountLinkEntity::getWecomMemberId, memberIds))
                .forEach(value -> result.put(value.getWecomMemberId(), value.getUserAccountId()));
        return result;
    }

    private static Member view(WecomMemberEntity value, String accountId) {
        return new Member(value.getUserId(), value.getName(), value.getPosition(), accountId);
    }

    private static Set<String> normalized(Set<String> values, String emptyMessage) {
        if (values == null) throw PlatformExceptions.unprocessable("WECOM_MEMBERS_REQUIRED", emptyMessage);
        if (values.size() > 100) {
            throw PlatformExceptions.unprocessable("WECOM_MEMBER_LIMIT", "单次最多处理 100 名企业微信成员");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 128) {
                throw PlatformExceptions.unprocessable("WECOM_USER_ID_INVALID", "企业微信成员 userid 无效");
            }
            result.add(value.trim());
        }
        return result;
    }
}
