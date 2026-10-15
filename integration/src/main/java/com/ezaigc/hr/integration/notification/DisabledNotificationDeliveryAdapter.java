package com.ezaigc.hr.integration.notification;

import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort;
import org.springframework.stereotype.Component;

@Component
public class DisabledNotificationDeliveryAdapter implements NotificationDeliveryPort {

    @Override
    public DeliveryResult deliver(NotificationMessage message) {
        return DeliveryResult.disabled("外部通知渠道尚未配置；请复制模板并记录人工发送，不影响本地面试事实");
    }
}
