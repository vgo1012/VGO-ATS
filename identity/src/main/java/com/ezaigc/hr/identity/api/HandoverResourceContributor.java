package com.ezaigc.hr.identity.api;

import java.util.List;

public interface HandoverResourceContributor {

    String resourceType();

    List<HandoverResource> preview(String sourceUserId);

    HandoverTransferResult transfer(
            String resourceId,
            String sourceUserId,
            String targetUserId,
            String actorUserId,
            String reason);
}
