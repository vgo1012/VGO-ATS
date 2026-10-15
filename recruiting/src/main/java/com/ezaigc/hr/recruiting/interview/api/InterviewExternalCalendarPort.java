package com.ezaigc.hr.recruiting.interview.api;

import java.time.Instant;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/** 企业微信日程/会议出站边界。实现不得泄露 access token 或原始远端响应。 */
public interface InterviewExternalCalendarPort {

    @Value
    @Accessors(fluent = true)
    class Command {
        String type;
        String externalObjectId;
        String title;
        String description;
        String adminUserId;
        List<String> inviteeUserIds;
        Instant startAt;
        Instant endAt;
        String timezoneId;
        String location;
        int reminderSeconds;
        boolean waitingRoomEnabled;
        boolean allowEnterBeforeHost;
        boolean enterMuteEnabled;
    }

    @Value
    @Accessors(fluent = true)
    class Result {
        boolean succeeded;
        boolean retryable;
        String externalObjectId;
        String meetingCode;
        String meetingLink;
        String remoteStatus;
        String errorCode;
        String errorDetail;
        boolean unknownCreate;

        public Result(boolean succeeded, boolean retryable, String externalObjectId,
                String meetingCode, String meetingLink, String remoteStatus,
                String errorCode, String errorDetail) {
            this(succeeded, retryable, externalObjectId, meetingCode, meetingLink, remoteStatus,
                    errorCode, errorDetail, false);
        }

        private Result(boolean succeeded, boolean retryable, String externalObjectId,
                String meetingCode, String meetingLink, String remoteStatus,
                String errorCode, String errorDetail, boolean unknownCreate) {
            this.succeeded = succeeded;
            this.retryable = retryable;
            this.externalObjectId = externalObjectId;
            this.meetingCode = meetingCode;
            this.meetingLink = meetingLink;
            this.remoteStatus = remoteStatus;
            this.errorCode = errorCode;
            this.errorDetail = errorDetail;
            this.unknownCreate = unknownCreate;
        }

        /** CREATE 仅在确认未发送或远端明确拒绝时使用；网络无确认必须返回 unknownCreate。 */
        public static Result failed(boolean retryable, String code, String detail) {
            return new Result(false, retryable, null, null, null, null, code, detail);
        }

        public static Result unknownCreate(String code, String detail) {
            return new Result(false, false, null, null, null, null, code, detail, true);
        }
    }

    Result create(Command command);

    Result update(Command command);

    Result cancel(Command command);
}
