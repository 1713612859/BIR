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
import com.zuno.bir.enums.ItemTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 零售订单计算器
 * <p>
 * 业务规则：
 * - 政府折扣人群只能有一个（不能多个）
 * - 如果没有政府折扣，支持整单按金额折扣或百分比折扣
 * - 整单折扣逻辑：Gross sales - 常规折扣，再去计算税相关操作
 * <p>
 * 计算逻辑：
 * 1. 如果有政府折扣：使用逐项计算模式（ItemLevelProcessor逻辑）
 * 2. 如果没有政府折扣但有整单折扣：先计算Gross - 常规折扣，再计算税
 * 3. 如果都没有：正常计算
 */
public class RetailOrderCalculator implements OrderCalculator {

    /**
     * 订单计算精度
     */
    private static final int CALC_SCALE = 4;
    /**
     * 最终金额精度
     */
    private static final int FINAL_SCALE = 2;

    /**
     * 支持的订单类型
     */
    @Override
    public BusinessType getSupportedBusinessType() {
        return BusinessType.RETAIL;
    }

    /**
     * 订单计算
     * @param context 计算上下文，包含订单、商品项、折扣等信息
     * @return
     */
    @Override
    public CalculationResult calculate(CalculationContext context) {
        // 订单信息
        SalesOrder order = context.getOrder();
        // 商品项
        List<SalesOrderItem> items = context.getItems();
        // 政府折扣
        List<GovernmentDiscount> discounts = context.getGovernmentDiscounts();

        // 确保订单对象保存当前计算用到的明细，便于后续打印和预览
        order.setItems(items);
        order.setGovDiscounts(discounts);
        
        // 重置订单累计金额
        DiscountHelpers.resetTotals(order);
        if (discounts != null) {
            DiscountHelpers.resetDiscountAmounts(discounts);
        }
        
        // 判断计算模式
        boolean hasGovernmentDiscount = discounts != null && !discounts.isEmpty();
        boolean hasWholeOrderDiscount = hasWholeOrderDiscount(context);
        
        CalculationResult result;
        if (hasGovernmentDiscount) {
            // 模式1：有政府折扣 - 使用逐项计算模式
            result = calculateWithGovernmentDiscount(context);
        } else if (hasWholeOrderDiscount) {
            // 模式2：有整单折扣（金额或百分比）- 使用整单折扣计算
            result = calculateWithWholeOrderDiscount(context);
        } else {
            // 模式3：无折扣 - 正常计算
            result = calculateWithoutDiscount(context);
        }

        // 统一计算 Amount Due，保证测试和实际流程一致
        DiscountHelpers.calculateFinalAmountDue(order, items);
        return result;
    }
    
    /**
     * 检查是否有整单折扣
     */
    private boolean hasWholeOrderDiscount(CalculationContext context) {
        BigDecimal amount = context.getWholeOrderDiscountAmount();
        BigDecimal percent = context.getWholeOrderDiscountPercent();
        
        return (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) ||
               (percent != null && percent.compareTo(BigDecimal.ZERO) > 0);
    }
    
    /**
     * 模式1：有政府折扣 - 逐项计算
     */
    private CalculationResult calculateWithGovernmentDiscount(CalculationContext context) {
        SalesOrder order = context.getOrder();
        List<SalesOrderItem> items = context.getItems();
        List<GovernmentDiscount> discounts = context.getGovernmentDiscounts();
        
        // 使用ItemLevelCalculator的逐项计算逻辑
        ItemLevelCalculator.DiscountAggregator aggregator = new ItemLevelCalculator.DiscountAggregator();
        
        for (SalesOrderItem item : items) {
            ItemLevelCalculator.processItem(order, item, discounts, BusinessType.RETAIL, aggregator);
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
        
        // 计算服务费基础（零售业态通常不需要服务费，但保留接口）
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
    
    /**
     * 模式2：有整单折扣（金额或百分比）
     * <p>
     * 计算逻辑：Gross sales - 常规折扣，再去计算税相关操作
     */
    private CalculationResult calculateWithWholeOrderDiscount(CalculationContext context) {
        SalesOrder order = context.getOrder();
        List<SalesOrderItem> items = context.getItems();
        
        // 1. 计算总Gross和总常规折扣
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalRegDisc = BigDecimal.ZERO;
        
        for (SalesOrderItem item : items) {
            BigDecimal itemGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            BigDecimal regDisc = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
            totalGross = totalGross.add(itemGross);
            totalRegDisc = totalRegDisc.add(regDisc);
        }
        
        // 2. 计算基础金额：Gross - 常规折扣
        BigDecimal baseAmount = totalGross.subtract(totalRegDisc).max(BigDecimal.ZERO);
        
        // 3. 计算整单折扣
        BigDecimal wholeOrderDiscount = calculateWholeOrderDiscount(context, baseAmount);
        
        // 4. 整单折扣不参与计算任何税
        // 计算逻辑：Gross - 常规折扣 - 整单折扣，然后从这个金额计算税
        BigDecimal afterDiscountAmount = baseAmount.subtract(wholeOrderDiscount).max(BigDecimal.ZERO);
        
        // 5. 根据商品VAT类型计算税（从折扣后的金额计算）
        // 需要区分VATABLE和VAT_EXEMPT商品
        BigDecimal totalVatable = BigDecimal.ZERO;
        BigDecimal totalVatExempt = BigDecimal.ZERO;
        BigDecimal totalZeroRated = BigDecimal.ZERO;
        BigDecimal totalVatAmount = BigDecimal.ZERO;
        
        // 按商品比例分摊整单折扣
        if (baseAmount.compareTo(BigDecimal.ZERO) > 0) {
            for (SalesOrderItem item : items) {
                BigDecimal itemGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
                BigDecimal regDisc = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
                BigDecimal itemBaseGross = itemGross.subtract(regDisc).max(BigDecimal.ZERO);
                
                // 计算该商品在基础金额中的比例
                BigDecimal itemRatio = itemBaseGross.divide(baseAmount, CALC_SCALE, RoundingMode.HALF_UP);
                BigDecimal itemDiscount = wholeOrderDiscount.multiply(itemRatio).setScale(FINAL_SCALE, RoundingMode.HALF_UP);
                BigDecimal itemAfterDiscount = itemBaseGross.subtract(itemDiscount).max(BigDecimal.ZERO);
                
                // 根据VAT类型计算税（整单折扣不参与税计算，所以从折扣后金额计算税）
                String vatType = item.getVatType() != null ? item.getVatType() : "VATABLE";
                
                if ("VATABLE".equalsIgnoreCase(vatType)) {
                    // 含税商品：从折扣后金额反算净额和税额
                    // 整单折扣不参与税计算，所以税是基于折扣后的金额计算的
                    BigDecimal itemNet = itemAfterDiscount.divide(DiscountHelpers.VAT_DIVISOR, FINAL_SCALE, RoundingMode.HALF_UP);
                    BigDecimal itemVat = itemAfterDiscount.subtract(itemNet);
                    
                    item.setVatableSales(itemNet);
                    item.setVatAmount(itemVat);
                    // item.setAmount 用于最终支付金额，这里设置为折扣后的含税金额
                    item.setAmount(itemAfterDiscount);
                    
                    totalVatable = totalVatable.add(itemNet);
                    totalVatAmount = totalVatAmount.add(itemVat);
                } else if ("VAT_EXEMPT".equalsIgnoreCase(vatType)) {
                    // 不含税商品：直接使用折扣后金额
                    item.setVatExemptSales(itemAfterDiscount);
                    item.setVatAmount(BigDecimal.ZERO);
                    item.setAmount(itemAfterDiscount);
                    
                    totalVatExempt = totalVatExempt.add(itemAfterDiscount);
                } else if ("ZERO_RATED".equalsIgnoreCase(vatType)) {
                    // 零税率商品
                    item.setZeroRatedSales(itemAfterDiscount);
                    item.setVatAmount(BigDecimal.ZERO);
                    item.setAmount(itemAfterDiscount);
                    
                    totalZeroRated = totalZeroRated.add(itemAfterDiscount);
                }
            }
        }
        
        // 6. 填充订单总额
        DiscountHelpers.fillOrderTotals(order,
                totalGross,
                totalRegDisc,
                wholeOrderDiscount, // 整单折扣作为政府折扣字段（虽然实际不是政府折扣）
                totalVatable,
                totalVatExempt,
                totalZeroRated,
                totalVatAmount);
        
        // 计算服务费基础
        BigDecimal scBasis = totalVatable.add(totalVatAmount).add(totalVatExempt).add(totalZeroRated);
        
        return CalculationResult.builder()
                .totalGrossSales(totalGross)
                .totalRegularDiscount(totalRegDisc)
                .totalGovernmentDiscount(wholeOrderDiscount)
                .totalVatableSales(totalVatable)
                .totalVatExemptSales(totalVatExempt)
                .totalZeroRatedSales(totalZeroRated)
                .totalVatAmount(totalVatAmount)
                .serviceChargeBasis(scBasis)
                .build();
    }
    
    /**
     * 计算整单折扣金额
     */
    private BigDecimal calculateWholeOrderDiscount(CalculationContext context, BigDecimal baseAmount) {
        BigDecimal amount = context.getWholeOrderDiscountAmount();
        BigDecimal percent = context.getWholeOrderDiscountPercent();
        
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            // 金额折扣
            return amount.min(baseAmount); // 不能超过基础金额
        } else if (percent != null && percent.compareTo(BigDecimal.ZERO) > 0) {
            // 百分比折扣
            BigDecimal maxPercent = new BigDecimal("1.00"); // 100%
            BigDecimal actualPercent = percent.min(maxPercent);
            return baseAmount.multiply(actualPercent).setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * 模式3：无折扣 - 正常计算
     */
    private CalculationResult calculateWithoutDiscount(CalculationContext context) {
        SalesOrder order = context.getOrder();
        List<SalesOrderItem> items = context.getItems();
        
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalRegDisc = BigDecimal.ZERO;
        BigDecimal totalVatable = BigDecimal.ZERO;
        BigDecimal totalVatExempt = BigDecimal.ZERO;
        BigDecimal totalZeroRated = BigDecimal.ZERO;
        BigDecimal totalVatAmount = BigDecimal.ZERO;
        
        for (SalesOrderItem item : items) {
            BigDecimal itemGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            BigDecimal regDisc = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
            BigDecimal baseGross = itemGross.subtract(regDisc).max(BigDecimal.ZERO);
            
            totalGross = totalGross.add(itemGross);
            totalRegDisc = totalRegDisc.add(regDisc);
            
            String vatType = item.getVatType() != null ? item.getVatType() : "VATABLE";
            
            if ("VATABLE".equalsIgnoreCase(vatType)) {
                BigDecimal itemNet = baseGross.divide(DiscountHelpers.VAT_DIVISOR, FINAL_SCALE, RoundingMode.HALF_UP);
                BigDecimal itemVat = baseGross.subtract(itemNet);
                
                item.setVatableSales(itemNet);
                item.setVatAmount(itemVat);
                item.setAmount(baseGross);
                
                totalVatable = totalVatable.add(itemNet);
                totalVatAmount = totalVatAmount.add(itemVat);
            } else if ("VAT_EXEMPT".equalsIgnoreCase(vatType)) {
                item.setVatExemptSales(baseGross);
                item.setVatAmount(BigDecimal.ZERO);
                item.setAmount(baseGross);
                
                totalVatExempt = totalVatExempt.add(baseGross);
            } else if ("ZERO_RATED".equalsIgnoreCase(vatType)) {
                item.setZeroRatedSales(baseGross);
                item.setVatAmount(BigDecimal.ZERO);
                item.setAmount(baseGross);
                
                totalZeroRated = totalZeroRated.add(baseGross);
            }
        }
        
        DiscountHelpers.fillOrderTotals(order,
                totalGross,
                totalRegDisc,
                BigDecimal.ZERO,
                totalVatable,
                totalVatExempt,
                totalZeroRated,
                totalVatAmount);
        
        BigDecimal scBasis = totalVatable.add(totalVatAmount).add(totalVatExempt).add(totalZeroRated);
        
        return CalculationResult.builder()
                .totalGrossSales(totalGross)
                .totalRegularDiscount(totalRegDisc)
                .totalGovernmentDiscount(BigDecimal.ZERO)
                .totalVatableSales(totalVatable)
                .totalVatExemptSales(totalVatExempt)
                .totalZeroRatedSales(totalZeroRated)
                .totalVatAmount(totalVatAmount)
                .serviceChargeBasis(scBasis)
                .build();
    }
}

