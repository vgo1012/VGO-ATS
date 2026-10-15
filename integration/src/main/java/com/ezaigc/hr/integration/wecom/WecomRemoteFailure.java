package com.ezaigc.hr.integration.wecom;

final class WecomRemoteFailure extends RuntimeException {
    private final String status;

    WecomRemoteFailure(String status, String message) {
        super(message, null, false, false);
        this.status = status;
    }

    String status() {
        return status;
    }

    static WecomRemoteFailure invalidResponse() {
        return new WecomRemoteFailure("INVALID_RESPONSE", "企业微信响应不完整或不一致，本次预览未发布任何数据");
    }

    static WecomRemoteFailure fromCode(int code) {
        return switch (code) {
            case 60020 -> new WecomRemoteFailure("IP_NOT_ALLOWED", "服务器出口 IP 尚未加入企业微信可信 IP，请部署后配置白名单再试");
            case 40001, 40013, 40014, 42001 -> new WecomRemoteFailure("CREDENTIALS_REJECTED", "企业微信凭据无效或已过期，请检查企业 ID 和应用密钥");
            case 48002, 60011, 60001 -> new WecomRemoteFailure("PERMISSION_DENIED", "企业微信应用缺少通讯录权限或可见范围，请管理员核实授权");
            case 45009, 45011 -> new WecomRemoteFailure("RATE_LIMITED", "企业微信调用频率受限，请等待冷却结束后重试");
            default -> new WecomRemoteFailure("REMOTE_REJECTED", "企业微信未完成本次读取，请核实应用权限与运行条件后重试");
        };
    }
}
