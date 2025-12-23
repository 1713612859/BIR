package com.zuno.bir.engine.processor;


import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【已废弃】旧版折扣处理器接口
 * <p>
 * <b>⚠️ 注意：此接口已被 {@link com.zuno.bir.engine.core.OrderCalculator} 替代。</b>
 * <p>
 * <b>迁移指南：</b>
 * 请使用新的架构：
 * <ul>
 *   <li>使用 {@link com.zuno.bir.engine.core.OrderCalculator} 替代此接口</li>
 *   <li>使用 {@link com.zuno.bir.engine.core.CalculationContext} 封装计算上下文</li>
 *   <li>使用 {@link com.zuno.bir.engine.core.CalculationResult} 封装计算结果</li>
 * </ul>
 * <p>
 * <b>保留原因：</b>
 * 为了向后兼容，暂时保留此接口。部分旧代码可能仍在使用。
 *
 * @deprecated 请使用 {@link com.zuno.bir.engine.core.OrderCalculator} 替代
 */
@Deprecated
public interface DiscountProcessor {
    /**
     * 执行订单的折扣和税费计算。
     * <p>
     * 实现该方法的类将负责具体的计算逻辑，并将计算结果填充回
     * {@link SalesOrder}, {@link SalesOrderItem}, 和 {@link GovernmentDiscount} 对象中。
     *
     * @param order     销售订单对象，包含了订单级别的上下文信息。
     * @param items     订单中的所有商品项列表。
     * @param discounts 适用于此订单的政府折扣列表。
     * @param store     店铺信息，可能包含服务费率等配置。
     * @return {@link BigDecimal} 返回一个用于计算服务费的基础金额。通常，这个值是整单所有商品在应用折扣前的净销售额（Net Sales）总和。
     */
    BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts, Store store);
}
