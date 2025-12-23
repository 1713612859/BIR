package com.zuno.bir.engine;

import com.zuno.bir.engine.strategy.BusinessStrategy;
import com.zuno.bir.engine.strategy.BusinessStrategyFactory;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【已废弃】旧版折扣计算引擎
 * <p>
 * <b>⚠️ 注意：此类的旧架构已被 {@link com.zuno.bir.engine.core.NewDiscountEngine} 替代。</b>
 * <p>
 * <b>迁移指南：</b>
 * 请使用 {@link com.zuno.bir.engine.core.NewDiscountEngine#calculateOrder} 替代此类的
 * {@link #calculateOrder(SalesOrder, List, List, Store)} 方法。
 * <p>
 * <b>新架构优势：</b>
 * <ul>
 *   <li>更清晰的职责分离：使用 {@link com.zuno.bir.engine.core.OrderCalculator} 接口</li>
 *   <li>更好的业务规则验证：通过 {@link com.zuno.bir.engine.core.BusinessRuleValidator}</li>
 *   <li>更统一的计算上下文：通过 {@link com.zuno.bir.engine.core.CalculationContext}</li>
 *   <li>更标准化的计算结果：通过 {@link com.zuno.bir.engine.core.CalculationResult}</li>
 * </ul>
 * <p>
 * <b>保留原因：</b>
 * 为了向后兼容，暂时保留此类。部分旧测试代码可能仍在使用。
 * 
 * @deprecated 请使用 {@link com.zuno.bir.engine.core.NewDiscountEngine} 替代
 */
@Deprecated
public class DiscountEngine {

    /**
     * 计算订单的折扣、税费和总金额。
     * <p>
     * 计算流程：
     * 1. 重置订单累计金额
     * 2. 根据业务类型获取对应的业务策略
     * 3. 执行策略计算（折扣、税费等）
     * 4. 计算服务费（仅餐饮业务）
     * 5. 计算最终应付金额
     *
     * @param order      销售订单对象，包含订单级别的信息，计算结果也将更新到此对象中。
     * @param items      订单中的商品项列表。
     * @param discounts  适用于此订单的政府折扣列表。
     * @param store      店铺信息，用于获取服务费等配置。
     */
    public static void calculateOrder(SalesOrder order, List<SalesOrderItem> items, 
                                     List<GovernmentDiscount> discounts, Store store) {

        // 1. 在计算开始前，重置订单中的所有累计金额
        DiscountHelpers.resetTotals(order);
        
        // 2. 根据业务类型获取对应的业务策略
        BusinessStrategy strategy = BusinessStrategyFactory.getStrategy(order);
        
        if (strategy == null) {
            // 如果找不到对应的策略，使用默认的ItemLevelProcessor
            throw new IllegalArgumentException(
                "Unsupported business type: " + order.getBusinessType() + 
                ". Please ensure the business type is one of: RETAIL, DINING, FAST_FOOD");
        }

        // 3. 执行策略计算：调用选定策略的 calculate 方法
        // 该方法会返回一个用于计算服务费的基础金额（通常是净销售额的累加）
        BigDecimal totalNetSalesAccumulator = strategy.calculate(order, items, discounts, store);

        // 4. 计算服务费
        // 仅当业务类型为餐饮且店铺信息存在时，才计算服务费
        BusinessType bizType = DiscountHelpers.parseBusinessType(order.getBusinessType());
        if (bizType == BusinessType.DINING && store != null) {
            DiscountHelpers.calculateServiceCharge(order, store, totalNetSalesAccumulator);
        }

        // 5. 最终汇总：根据所有商品项计算后的金额，计算订单的最终应付金额
        DiscountHelpers.calculateFinalAmountDue(order, items);
    }
}
