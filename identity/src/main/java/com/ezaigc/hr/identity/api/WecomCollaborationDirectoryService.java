package com.ezaigc.hr.identity.api;

import java.util.Map;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

/** 企业微信协作身份查询边界，不授予本地登录或业务权限。 */
public interface WecomCollaborationDirectoryService {

    @Value
    @Accessors(fluent = true)
    class Member {
        String userId;
        String name;
        String position;
        String linkedUserAccountId;
    }

    /** 将本地账号解析为显式关联且当前可见的企业微信 userid；缺失关联时拒绝。 */
    Map<String, String> requireLinkedUserIds(Set<String> localUserIds);

    /** 校验并返回当前可见、已激活的企业微信成员。 */
    Map<String, Member> requireMembers(Set<String> sourceUserIds);

}
