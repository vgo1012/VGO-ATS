package com.ezaigc.hr.common.infrastructure.outbox;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;

/** 校验持久事件与业务工作绑定；重复通知不重新排队或重置已经完成的工作。 */
public interface OutboxDeliveryPort {
    TaskSnapshot verify(OutboxEnvelope envelope);

    /** SQL 轮询也读取并校验原始信封，恢复 trace 与资源绑定，不依赖消息已到达。 */
    OutboxEnvelope forTask(TaskSnapshot task);
}
