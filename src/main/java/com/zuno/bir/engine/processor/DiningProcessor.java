package com.zuno.bir.engine.processor;

import com.zuno.bir.engine.DiscountHelpers;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 餐饮业务处理器 (Dining Processor) - 修正版
 * <p>
 * 职责：处理餐饮业务类型的折扣计算。
 * <p>
 * 业务规则：
 * - 餐饮支持SC、PWD、NAAC、MOV、DIPLOMATIC（不支持SP）
 * - 只有餐饮支持整单折扣（按人数比例分摊）
 * - 整单折扣支持：SC、PWD、NAAC、MOV（20%折扣率）
 * - 整单折扣不支持：SP、DIPLOMATIC
 * - DIPLOMATIC和其他折扣互斥，不支持整单折扣
 * <p>
 * 路由逻辑：
 * - 如果是团餐 (Group Meal) 模式，则执行按人头分摊折扣的逻辑。
 * - 如果是单点 (Item-level) 模式，则复用 {@link ItemLevelProcessor} 的逐项计算逻辑。
 * - 如果存在DIPLOMATIC折扣，即使标记为团餐，也使用单品折扣逻辑。
 */
public class DiningProcessor implements DiscountProcessor {

    private final ItemLevelProcessor itemProcessor = new ItemLevelProcessor();
    private static final int CALC_SCALE = 4;  // 中间计算精度
    private static final int FINAL_SCALE = 2; // 最终存储精度

    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts, Store store) {
        if (order.isGroupMeal()) {
            return handleGroupMeal(order, items, discounts);
        } else {
            // 单品折扣逻辑：复用 ItemLevelProcessor，天然支持“一个品对应一个人”或“一个人对应多个品”
            return itemProcessor.calculate(order, items, discounts, store);
        }
    }

    /**
     * 处理团餐的折扣计算（修正版）。
     * <p>
     * 业务规则：
     * - 同一个单可以有多个特殊折扣，按人数比例分摊
     * - 整单折扣支持：SC、PWD、NAAC、MOV（20%折扣率）
     * - 整单折扣不支持：SP、DIPLOMATIC
     * - DIPLOMATIC和其他折扣互斥，如果存在DIPLOMATIC折扣，则不应使用整单折扣逻辑
     * <p>
     * 算法示例：
     * - 5个人用餐，总金额100块。二个人MOV，一个人PWD，一个人NAAC，一个人无折扣
     * - 100/5*2 MOV折扣，100/5*1 PWD折扣，100/5*1 NAAC折扣，100/5*1 无折扣
     */
    private BigDecimal handleGroupMeal(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts) {
        
        // 【关键检查】DIPLOMATIC折扣不支持整单折扣，如果存在DIPLOMATIC折扣，应使用单品折扣逻辑
        if (DiscountHelpers.containsDiplomaticDiscount(discounts)) {
            // DIPLOMATIC折扣和其他折扣互斥，不支持整单折扣，回退到单品折扣逻辑
            return itemProcessor.calculate(order, items, discounts, null);
        }

        // 1. 汇总整单所有商品的 Gross, Net, 和 VAT
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalRegDisc = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;

        for (SalesOrderItem item : items) {
            // Gross, Net, VAT
            BigDecimal gross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            BigDecimal r = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
            totalGross = totalGross.add(gross);
            totalRegDisc = totalRegDisc.add(r);

            BigDecimal baseGross = gross.subtract(r).max(BigDecimal.ZERO);
            String vatType = item.getVatType() != null ? item.getVatType() : "VATABLE";

            totalNet = totalNet.add(DiscountHelpers.calculateNetPrice(baseGross, vatType, CALC_SCALE));
            totalVat = totalVat.add(DiscountHelpers.calculateVatAmount(baseGross, vatType, CALC_SCALE));
        }

        // 2. 计算人均份额 (Share) = 总额 / 总人数
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
        
        // 服务费基础累加器
        BigDecimal scBasisAccumulator = BigDecimal.ZERO;

        int processedPax = 0;

        // 4. 处理享受折扣的人群 (支持多个特殊折扣，按人数累加)
        // 整单折扣只支持：SC、PWD、NAAC、MOV（20%折扣率）
        if (discounts != null) {
            for (GovernmentDiscount gd : discounts) {
                DiscountType type = DiscountHelpers.parseDiscountType(gd.getPersonType());
                
                // 检查该折扣类型是否支持整单折扣
                if (!DiscountHelpers.isGroupMealDiscountAllowed(type)) {
                    // 如果折扣类型不支持整单折扣（如SP、DIPLOMATIC），跳过该折扣
                    // 注意：DIPLOMATIC已经在方法开头检查过了，这里主要是防止SP
                    continue;
                }
                
                int count = gd.getCount() != null ? gd.getCount() : 0;
                if (count <= 0) continue;
                processedPax += count;

                // 计算该组人群的份额：Share * Count
                BigDecimal groupNet = shareNet.multiply(new BigDecimal(count));
                BigDecimal groupVat = shareVat.multiply(new BigDecimal(count));

                // 整单折扣统一使用20%折扣率（SC、PWD、NAAC、MOV都是20%）
                BigDecimal rate = new BigDecimal("0.20");
                BigDecimal discAmt = groupNet.multiply(rate).setScale(FINAL_SCALE, RoundingMode.HALF_UP);

                totalDiscountAmount = totalDiscountAmount.add(discAmt);
                gd.setDiscount(discAmt);

                // 核心：享受折扣的人群免除其对应的VAT份额 (Tax Reduction)
                totalTaxReduction = totalTaxReduction.add(groupVat);
                gd.setLessVat(groupVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
                gd.setAddVat(BigDecimal.ZERO);

                // 其消费贡献（净额 - 折扣）归入免税销售额
                BigDecimal actualRevenue = groupNet.subtract(discAmt);
                totalExemptSales = totalExemptSales.add(actualRevenue);

                // 【修正1】折扣人群的服务费基础 = 净额 - 折扣 (即 Exempt Sales)
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

            // 【修正2】普通人群的服务费基础 = 净额 (折前)
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
                finalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP)
        );

        // 【修正3】回填 Item Amount，解决 Amount Due 计算错误的问题
        // 计算整单商品应付总额 (不含服务费) = Vatable + VAT + Exempt
        BigDecimal totalPayableForItems = totalVatableSales.add(finalVatAmount).add(totalExemptSales);
        
        // 按原始 Gross 占比分摊回每个 Item
        if (totalGross.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal runningTotal = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                SalesOrderItem item = items.get(i);
                if (i == items.size() - 1) {
                    // 最后一个item用减法，避免舍入误差
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
            for (SalesOrderItem item : items) item.setAmount(BigDecimal.ZERO);
        }

        // 返回修正后的服务费计算基础
        return scBasisAccumulator.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
    }
}
