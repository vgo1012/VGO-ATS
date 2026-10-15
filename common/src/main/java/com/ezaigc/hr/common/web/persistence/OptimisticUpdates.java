package com.ezaigc.hr.common.web.persistence;

import com.ezaigc.hr.common.web.error.PlatformExceptions;

/**
 * 将持久化层受影响行数转换为稳定的乐观并发冲突。
 */
public final class OptimisticUpdates {

    private OptimisticUpdates() {
    }

    /**
     * 要求命令恰好更新一行，否则抛出调用方指定的冲突错误。
     */
    public static void requireOne(int changed, String code, String message) {
        if (changed != 1) {
            throw PlatformExceptions.conflict(code, message);
        }
    }
}
