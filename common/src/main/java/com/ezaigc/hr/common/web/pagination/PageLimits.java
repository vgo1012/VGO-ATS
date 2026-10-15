package com.ezaigc.hr.common.web.pagination;

import static com.ezaigc.hr.common.web.error.PlatformExceptions.badRequest;

public final class PageLimits {

    public static final int DEFAULT = 20;
    public static final int MAXIMUM = 100;
    /**
     * 数据库内部批处理的统一物理阀门；公开接口仍由 MAXIMUM 限制为 100。
     */
    public static final int MAXIMUM_PHYSICAL_FETCH = 500;

    private PageLimits() {
    }

    public static int resolve(Integer requested, int defaultValue, int maximum) {
        if (defaultValue < 1 || maximum < defaultValue) {
            throw new IllegalArgumentException("page limit bounds are invalid");
        }
        if (requested == null) {
            return defaultValue;
        }
        if (requested < 1 || requested > maximum) {
            throw badRequest(
                    "PAGE_LIMIT_INVALID", "limit 必须在 1～" + maximum + " 之间");
        }
        return requested;
    }

    public static int resolve(Integer requested) {
        return resolve(requested, DEFAULT, MAXIMUM);
    }
}
