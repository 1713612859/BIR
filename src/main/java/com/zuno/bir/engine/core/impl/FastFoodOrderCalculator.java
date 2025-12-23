package com.zuno.bir.engine.core.impl;

import com.zuno.bir.engine.DiscountHelpers;
import com.zuno.bir.engine.ItemLevelCalculator;
import com.zuno.bir.engine.core.CalculationContext;
import com.zuno.bir.engine.core.CalculationResult;
import com.zuno.bir.engine.core.OrderCalculator;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.ItemTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 快餐订单计算器
 * <p>
 * 业务规则：
 * - 严格符合税务局要求
 * - 和餐饮业态模式一致（单品折扣模式）
 * - 只能添加单个政府折扣人群
 * - 不支持整单折扣（按人数比例分摊）
 * <p>
 * 计算逻辑：
 * - 使用逐项计算模式（ItemLevelProcessor逻辑）
 */
public class FastFoodOrderCalculator implements OrderCalculator {
    
    private static final int FINAL_SCALE = 2;
    
    @Override
    public BusinessType getSupportedBusinessType() {
        return BusinessType.FAST_FOOD;
    }
    
    @Override
    public CalculationResult calculate(CalculationContext context) {
        SalesOrder order = context.getOrder();
        List<SalesOrderItem> items = context.getItems();
        List<GovernmentDiscount> discounts = context.getGovernmentDiscounts();

        // 绑定明细到订单，便于后续打印和预览
        order.setItems(items);
        order.setGovDiscounts(discounts);
        
        // 重置订单累计金额
        DiscountHelpers.resetTotals(order);
        if (discounts != null) {
            DiscountHelpers.resetDiscountAmounts(discounts);
        }
        
        // 快餐业态只支持单品折扣模式，使用逐项计算
        ItemLevelCalculator.DiscountAggregator aggregator = new ItemLevelCalculator.DiscountAggregator();
        
        for (SalesOrderItem item : items) {
            ItemLevelCalculator.processItem(order, item, discounts, BusinessType.FAST_FOOD, aggregator);
        }
        
        // 填充订单总额
        DiscountHelpers.fillOrderTotals(order,
                aggregator.totalGross,
                aggregator.totalRegDisc,
                aggregator.totalGovDisc,
                aggregator.totalVatable.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                aggregator.totalVatExempt.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                aggregator.totalZeroRated.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                aggregator.totalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
        
        // 填充LessVAT/AddVAT显示
        if (discounts != null && !discounts.isEmpty()) {
            GovernmentDiscount first = discounts.get(0);
            first.setLessVat(aggregator.displayLessVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
            first.setAddVat(aggregator.globalAddBackVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
        }
        
        // 计算服务费基础（快餐业态通常不需要服务费，但保留接口）
        BigDecimal scBasis = aggregator.totalVatable
                .add(aggregator.totalVatAmount)
                .add(aggregator.totalVatExempt)
                .add(aggregator.totalZeroRated);
        
        CalculationResult result = CalculationResult.builder()
                .totalGrossSales(aggregator.totalGross)
                .totalRegularDiscount(aggregator.totalRegDisc)
                .totalGovernmentDiscount(aggregator.totalGovDisc)
                .totalVatableSales(aggregator.totalVatable.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalVatExemptSales(aggregator.totalVatExempt.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalZeroRatedSales(aggregator.totalZeroRated.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalVatAmount(aggregator.totalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .serviceChargeBasis(scBasis.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .build();

        // 统一计算 Amount Due
        DiscountHelpers.calculateFinalAmountDue(order, items);
        return result;
    }
}

