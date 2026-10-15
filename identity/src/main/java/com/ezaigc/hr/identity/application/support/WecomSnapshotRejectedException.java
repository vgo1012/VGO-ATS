package com.ezaigc.hr.identity.application.support;

import lombok.Getter;

@Getter
public class WecomSnapshotRejectedException extends RuntimeException {
    private final String code;

    public WecomSnapshotRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }
}
