package com.ezaigc.hr.common.infrastructure.concurrent;

public final class ExecutorNames {

    /**
     * AI 异步执行器名称。
     */
    public static final String AI = "aiTaskExecutor";
    /**
     * 匹配。
     */
    public static final String MATCHING = "matchingTaskExecutor";
    /**
     * 搜索。
     */
    public static final String SEARCH = "searchTaskExecutor";
    /**
     * 维护任务执行器名称。
     */
    public static final String MAINTENANCE = "maintenanceTaskExecutor";
    /** 文件报表独立执行器，避免大型表格占用在线、AI 或维护线程。 */
    public static final String REPORTING = "reportingTaskExecutor";
    public static final String DIRECTORY = "directoryTaskExecutor";
    /** 邮件网络读取与附件安全扫描独立执行，避免占用维护线程。 */
    public static final String MAIL = "mailTaskExecutor";
    /** 消息发布专用执行器，隔离 Broker 等待与维护任务。 */
    public static final String OUTBOX = "outboxTaskExecutor";
    /** Broker 非阻塞网络循环与确认回调分离，避免发布等待反向占用确认线程。 */
    public static final String BROKER_IO = "brokerIoExecutor";
    public static final String BROKER_CALLBACKS = "brokerCallbackExecutor";
    /** 消费容器阻塞取消息线程，与网络 IO 和确认回调隔离。 */
    public static final String BROKER_CONSUMERS = "brokerConsumerExecutor";

    private ExecutorNames() {
    }
}
