package com.zuno.bir.engine.core;

import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【核心引擎】折扣计算引擎 - 统一入口
 * <p>
 * <b>架构设计：</b>
 * 这是整个折扣计算系统的统一入口，采用策略模式 + 工厂模式的组合设计。
 * <p>
 * <b>核心流程：</b>
 * <ol>
 *   <li><b>解析业务类型</b>：从订单中解析业务类型（RETAIL/DINING/FAST_FOOD）</li>
 *   <li><b>创建计算上下文</b>：封装订单、商品项、折扣、店铺等信息到 {@link CalculationContext}</li>
 *   <li><b>验证业务规则</b>：通过 {@link BusinessRuleValidator} 验证业务约束</li>
 *   <li><b>获取计算器</b>：通过 {@link CalculatorFactory} 获取对应的 {@link OrderCalculator}</li>
 *   <li><b>执行计算</b>：调用计算器的 {@link OrderCalculator#calculate(CalculationContext)} 方法</li>
 *   <li><b>计算服务费</b>：仅餐饮业务需要计算服务费</li>
 *   <li><b>计算最终金额</b>：汇总所有商品金额 + 服务费 + 本地税 = 应付金额</li>
 * </ol>
 * <p>
 * <b>支持的业态：</b>
 * <ul>
 *   <li><b>RETAIL（零售）</b>：支持政府折扣和整单折扣（金额/百分比）</li>
 *   <li><b>DINING（餐饮）</b>：支持单品折扣和整单折扣（按人数分摊）</li>
 *   <li><b>FAST_FOOD（快餐）</b>：支持单品折扣，不支持整单折扣</li>
 * </ul>
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 * // 基本用法（无整单折扣）
 * NewDiscountEngine.calculateOrder(order, items, discounts, store);
 * 
 * // 零售整单金额折扣
 * NewDiscountEngine.calculateOrder(order, items, discounts, store, 
 *     new BigDecimal("50.00"), null);
 * 
 * // 零售整单百分比折扣（10%）
 * NewDiscountEngine.calculateOrder(order, items, discounts, store, 
 *     null, new BigDecimal("0.10"));
 * }</pre>
 * <p>
 * <b>设计模式：</b>
 * <ul>
 *   <li><b>策略模式</b>：不同业态使用不同的 {@link OrderCalculator} 实现</li>
 *   <li><b>工厂模式</b>：通过 {@link CalculatorFactory} 创建计算器实例</li>
 *   <li><b>上下文模式</b>：使用 {@link CalculationContext} 封装计算所需的所有数据</li>
 * </ul>
 */
public class NewDiscountEngine {
    
    /**
     * 计算订单的折扣、税费和总金额
     * <p>
     * 这是新的统一计算入口，替代旧的DiscountEngine.calculateOrder方法。
     *
     * @param order 销售订单对象
     * @param items 订单商品项列表
     * @param discounts 政府折扣列表
     * @param store 店铺信息
     */
    public static void calculateOrder(SalesOrder order, List<SalesOrderItem> items, 
                                     List<GovernmentDiscount> discounts, Store store) {
        calculateOrder(order, items, discounts, store, null, null);
    }
    
    /**
     * 计算订单的折扣、税费和总金额（支持整单折扣）
     * <p>
     * 用于零售业态的整单金额折扣或百分比折扣。
     *
     * @param order 销售订单对象
     * @param items 订单商品项列表
     * @param discounts 政府折扣列表
     * @param store 店铺信息
     * @param wholeOrderDiscountAmount 整单折扣金额（可选）
     * @param wholeOrderDiscountPercent 整单折扣百分比（可选，0-1之间）
     */
    public static void calculateOrder(SalesOrder order, List<SalesOrderItem> items, 
                                     List<GovernmentDiscount> discounts, Store store,
                                     BigDecimal wholeOrderDiscountAmount, 
                                     BigDecimal wholeOrderDiscountPercent) {
        // 1. 解析业务类型
        BusinessType bizType = com.zuno.bir.engine.DiscountHelpers.parseBusinessType(order.getBusinessType());
        
        // 2. 创建计算上下文
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(store)
                .businessType(bizType)
                .wholeOrderDiscountAmount(wholeOrderDiscountAmount)
                .wholeOrderDiscountPercent(wholeOrderDiscountPercent)
                .build();
        
        // 3. 验证业务规则
        BusinessRuleValidator.validate(context);
        
        // 4. 获取对应的计算器
        OrderCalculator calculator = CalculatorFactory.getCalculator(bizType);
        
        // 5. 执行计算
        CalculationResult result = calculator.calculate(context);
        
        // 6. 计算服务费（仅餐饮业务）
        if (bizType == BusinessType.DINING && store != null) {
            com.zuno.bir.engine.DiscountHelpers.calculateServiceCharge(
                    order, store, result.getServiceChargeBasis());
        }
        
        // 7. 计算最终应付金额
        com.zuno.bir.engine.DiscountHelpers.calculateFinalAmountDue(order, items);
    }
}

