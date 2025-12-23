package com.zuno.bir.enums;

/**
 * 业务类型枚举
 * 用于区分不同类型的商业活动，这可能会影响折扣的适用性。
 */
public enum BusinessType {
    /**
     * 零售业
     */
    RETAIL,
    /**
     * 正餐、堂食类餐饮
     */
    DINING,
    /**
     * 快餐，也包括奶茶店等快速服务的餐饮形式
     */
    FAST_FOOD
}
