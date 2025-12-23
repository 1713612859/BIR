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
import com.zuno.bir.enums.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 餐饮订单计算器
 * <p>
 * 业务规则：
 * - 单品折扣模式：只能添加单个政府折扣人群
 * - 整单折扣模式：支持多个政府折扣人群（按人数比例分摊）
 * - 整单折扣支持：SC、PWD、NAAC、MOV（20%折扣率）
 * - 整单折扣不支持：SP、DIPLOMATIC
 * <p>
 * 计算逻辑：
 * 1. 如果是整单折扣模式（Group Meal）：按人数分摊折扣
 * 2. 如果是单品折扣模式：使用逐项计算
 */
public class DiningOrderCalculator implements OrderCalculator {
    
    private static final int CALC_SCALE = 4;
    private static final int FINAL_SCALE = 2;
    
    @Override
    public BusinessType getSupportedBusinessType() {
        return BusinessType.DINING;
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
        
        CalculationResult result;
        if (order.isGroupMeal()) {
            // 整单折扣模式：按人数分摊
            result = calculateGroupMeal(context);
        } else {
            // 单品折扣模式：逐项计算
            result = calculateItemLevel(context);
        }

        // 统一计算 Amount Due
        DiscountHelpers.calculateFinalAmountDue(order, items);
        return result;
    }
    
    /**
     * 整单折扣模式：按人数比例分摊
     * <p>
     * 算法：
     * - 计算整单总金额和人均份额
     * - 按不同折扣人群的人数分摊折扣
     * - 支持多个折扣人群（SC、PWD、NAAC、MOV）
     */
    private CalculationResult calculateGroupMeal(CalculationContext context) {
        SalesOrder order = context.getOrder();
        List<SalesOrderItem> items = context.getItems();
        List<GovernmentDiscount> discounts = context.getGovernmentDiscounts();
        
        // 【关键检查】DIPLOMATIC折扣不支持整单折扣
        if (DiscountHelpers.containsDiplomaticDiscount(discounts)) {
            // 回退到单品折扣逻辑
            return calculateItemLevel(context);
        }
        
        // 1. 汇总整单所有商品的 Gross, Net, 和 VAT
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalRegDisc = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        
        for (SalesOrderItem item : items) {
            BigDecimal gross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            BigDecimal r = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
            totalGross = totalGross.add(gross);
            totalRegDisc = totalRegDisc.add(r);
            
            BigDecimal baseGross = gross.subtract(r).max(BigDecimal.ZERO);
            String vatType = item.getVatType() != null ? item.getVatType() : "VATABLE";
            
            totalNet = totalNet.add(DiscountHelpers.calculateNetPrice(baseGross, vatType, CALC_SCALE));
            totalVat = totalVat.add(DiscountHelpers.calculateVatAmount(baseGross, vatType, CALC_SCALE));
        }
        
        // 2. 计算人均份额
        int totalPax = order.getPax() != null && order.getPax() > 0 ? order.getPax() : 1;
        BigDecimal bdPax = new BigDecimal(totalPax);
        
        BigDecimal shareNet = totalNet.divide(bdPax, CALC_SCALE, RoundingMode.HALF_UP);
        BigDecimal shareVat = totalVat.divide(bdPax, CALC_SCALE, RoundingMode.HALF_UP);
        
        // 3. 初始化累加器
        BigDecimal totalDiscountAmount = BigDecimal.ZERO;
        BigDecimal totalTaxReduction = BigDecimal.ZERO;
        BigDecimal finalVatAmount = BigDecimal.ZERO;
        BigDecimal totalVatableSales = BigDecimal.ZERO;
        BigDecimal totalExemptSales = BigDecimal.ZERO;
        BigDecimal scBasisAccumulator = BigDecimal.ZERO;
        
        int processedPax = 0;
        
        // 4. 处理享受折扣的人群（支持多个特殊折扣，按人数累加）
        if (discounts != null) {
            for (GovernmentDiscount gd : discounts) {
                DiscountType type = DiscountHelpers.parseDiscountType(gd.getPersonType());
                
                // 检查该折扣类型是否支持整单折扣
                if (!DiscountHelpers.isGroupMealDiscountAllowed(type)) {
                    continue; // 跳过不支持整单折扣的类型（如SP）
                }
                
                int count = gd.getCount() != null ? gd.getCount() : 0;
                if (count <= 0) continue;
                processedPax += count;
                
                // 计算该组人群的份额：Share * Count
                BigDecimal groupNet = shareNet.multiply(new BigDecimal(count));
                BigDecimal groupVat = shareVat.multiply(new BigDecimal(count));
                
                // 整单折扣统一使用20%折扣率
                BigDecimal rate = new BigDecimal("0.20");
                BigDecimal discAmt = groupNet.multiply(rate).setScale(FINAL_SCALE, RoundingMode.HALF_UP);
                
                totalDiscountAmount = totalDiscountAmount.add(discAmt);
                gd.setDiscount(discAmt);
                
                // 享受折扣的人群免除其对应的VAT份额
                totalTaxReduction = totalTaxReduction.add(groupVat);
                gd.setLessVat(groupVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
                gd.setAddVat(BigDecimal.ZERO);
                
                // 其消费贡献（净额 - 折扣）归入免税销售额
                BigDecimal actualRevenue = groupNet.subtract(discAmt);
                totalExemptSales = totalExemptSales.add(actualRevenue);
                
                // 折扣人群的服务费基础 = 净额 - 折扣
                scBasisAccumulator = scBasisAccumulator.add(actualRevenue);
            }
        }
        
        // 5. 处理不享受折扣的普通人群
        int remainingPax = totalPax - processedPax;
        if (remainingPax > 0) {
            BigDecimal groupNet = shareNet.multiply(new BigDecimal(remainingPax));
            BigDecimal groupVat = shareVat.multiply(new BigDecimal(remainingPax));
            
            totalVatableSales = totalVatableSales.add(groupNet);
            finalVatAmount = finalVatAmount.add(groupVat);
            
            // 普通人群的服务费基础 = 净额（折前）
            scBasisAccumulator = scBasisAccumulator.add(groupNet);
        }
        
        // 6. 汇总与回填
        BigDecimal totalGovDiscount = totalDiscountAmount.add(totalTaxReduction);
        
        if (discounts != null && !discounts.isEmpty()) {
            discounts.get(0).setLessVat(totalTaxReduction.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
            discounts.get(0).setAddVat(finalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
        }
        
        DiscountHelpers.fillOrderTotals(order,
                totalGross,
                totalRegDisc,
                totalGovDiscount,
                totalVatableSales.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                totalExemptSales.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                finalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
        
        // 回填 Item Amount，按原始 Gross 占比分摊
        BigDecimal totalPayableForItems = totalVatableSales.add(finalVatAmount).add(totalExemptSales);
        
        if (totalGross.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal runningTotal = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                SalesOrderItem item = items.get(i);
                if (i == items.size() - 1) {
                    item.setAmount(totalPayableForItems.subtract(runningTotal));
                } else {
                    BigDecimal itemGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
                    BigDecimal itemAmount = totalPayableForItems.multiply(itemGross)
                            .divide(totalGross, FINAL_SCALE, RoundingMode.HALF_UP);
                    item.setAmount(itemAmount);
                    runningTotal = runningTotal.add(itemAmount);
                }
            }
        } else {
            for (SalesOrderItem item : items) {
                item.setAmount(BigDecimal.ZERO);
            }
        }
        
        return CalculationResult.builder()
                .totalGrossSales(totalGross)
                .totalRegularDiscount(totalRegDisc)
                .totalGovernmentDiscount(totalGovDiscount)
                .totalVatableSales(totalVatableSales.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalVatExemptSales(totalExemptSales.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalZeroRatedSales(BigDecimal.ZERO)
                .totalVatAmount(finalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .serviceChargeBasis(scBasisAccumulator.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .build();
    }
    
    /**
     * 单品折扣模式：逐项计算
     * <p>
     * 规则：只能添加单个政府折扣人群
     */
    private CalculationResult calculateItemLevel(CalculationContext context) {
        SalesOrder order = context.getOrder();
        List<SalesOrderItem> items = context.getItems();
        List<GovernmentDiscount> discounts = context.getGovernmentDiscounts();
        
        // 使用ItemLevelCalculator的逐项计算逻辑
        ItemLevelCalculator.DiscountAggregator aggregator = new ItemLevelCalculator.DiscountAggregator();
        
        for (SalesOrderItem item : items) {
            ItemLevelCalculator.processItem(order, item, discounts, BusinessType.DINING, aggregator);
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
        
        // 计算服务费基础
        BigDecimal scBasis = aggregator.totalVatable
                .add(aggregator.totalVatAmount)
                .add(aggregator.totalVatExempt)
                .add(aggregator.totalZeroRated);
        
        return CalculationResult.builder()
                .totalGrossSales(aggregator.totalGross)
                .totalRegularDiscount(aggregator.totalRegDisc)
                .totalGovernmentDiscount(aggregator.totalGovDisc)
                .totalVatableSales(aggregator.totalVatable.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalVatExemptSales(aggregator.totalVatExempt.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalZeroRatedSales(aggregator.totalZeroRated.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .totalVatAmount(aggregator.totalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .serviceChargeBasis(scBasis.setScale(FINAL_SCALE, RoundingMode.HALF_UP))
                .build();
    }
}

