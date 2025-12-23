package com.zuno.bir.engine.processor;

import com.zuno.bir.engine.DiscountHelpers;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.DiscountType;
import com.zuno.bir.enums.ItemTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 零售整单处理器 (Retail Whole-Order Processor) - 最终修正版
 * <p>
 * 职责：为零售业务类型实现一种特殊的“整单”折扣计算模型。
 * <p>
 * 核心逻辑：
 * 1. 首先，遍历所有商品，计算出实际应该应用的总折扣额。这个计算是逐项进行的，
 *    根据每个商品的标签（MGS或VBN）应用不同的折扣率（20%或5%）。
 * 2. 然后，判断整个订单的最终税务状态。规则是：只要订单中包含了至少一个VBN商品的折扣，
 *    整个订单就被视为“含税”模式（Taxable）。只有当所有折扣都来自MGS商品时，订单才被视为“免税”模式（Exempt）。
 * 3. 根据最终的税务状态，对整个订单的总额进行处理：
 *    - **含税模式 (VBN/Taxable):** 最终支付金额 = (总基础金额 - 总折扣额)。然后从这个最终支付金额中反算出应税销售额和VAT。
 *    - **免税模式 (MGS Only):** 最终支付金额 = (总净额 - 总折扣额)。这部分金额全部归入免税销售额，VAT为0。
 * 4. 这种方法确保了无论内部如何计算，最终的税务分类（应税 vs 免税）是整单统一的，以VBN的含税规则为优先。
 */
public class RetailWholeOrderProcessor implements DiscountProcessor {

    private static final int TEMP_MATH_SCALE = 4; // 临时计算精度

    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts, Store store) {

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalRegDisc = BigDecimal.ZERO;
        BigDecimal totalBaseGross = BigDecimal.ZERO;
        BigDecimal scBasisAccumulator = BigDecimal.ZERO; // 服务费计算基础

        // 1. 第一次遍历：计算总Gross, 总BaseGross, 以及服务费计算基础 (所有商品的原始净额之和)
        for (SalesOrderItem item : items) {
            BigDecimal g = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            BigDecimal r = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
            BigDecimal baseGross = g.subtract(r).max(BigDecimal.ZERO);

            totalGross = totalGross.add(g);
            totalRegDisc = totalRegDisc.add(r);
            totalBaseGross = totalBaseGross.add(baseGross);

            // 计算该商品的净额并累加到服务费基础
            BigDecimal itemNet = baseGross.divide(DiscountHelpers.VAT_DIVISOR, TEMP_MATH_SCALE, RoundingMode.HALF_UP);
            scBasisAccumulator = scBasisAccumulator.add(itemNet);
        }

        // 2. 基于总BaseGross，计算整单的原始总净额和总税额
        BigDecimal totalNet = totalBaseGross.divide(DiscountHelpers.VAT_DIVISOR, TEMP_MATH_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalVat = totalBaseGross.subtract(totalNet);

        // 3. 第二次遍历：计算实际应用的总折扣额，并检查是否存在VBN折扣
        BigDecimal actualTotalDiscount = BigDecimal.ZERO;
        boolean containsVBNDiscount = false; // VBN标志位，它将决定整单的税务状态

        if (discounts != null && !discounts.isEmpty()) {
            GovernmentDiscount mainGd = discounts.get(0);
            DiscountType discType = DiscountHelpers.parseDiscountType(mainGd.getPersonType());

            // 此逻辑仅适用于老年人(SC)和残疾人(PWD)
            if (discType == DiscountType.SC || discType == DiscountType.PWD) {
                for (SalesOrderItem item : items) {
                    BigDecimal baseGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()))
                            .subtract(item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO);
                    BigDecimal itemNet = baseGross.divide(DiscountHelpers.VAT_DIVISOR, TEMP_MATH_SCALE, RoundingMode.HALF_UP);
                    ItemTag itemTag = DiscountHelpers.parseItemTag(item.getDiscountTag());

                    // 检查该商品是否允许被当前折扣人群在零售业态下折扣
                    if (DiscountHelpers.isDiscountAllowed(BusinessType.RETAIL, discType, itemTag)) {
                        BigDecimal rate = DiscountHelpers.getDiscountRate(discType, itemTag);
                        BigDecimal discAmt = itemNet.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                        actualTotalDiscount = actualTotalDiscount.add(discAmt);

                        // 如果这是一个VBN商品的折扣，设置标志位
                        if (itemTag == ItemTag.VBN) {
                            containsVBNDiscount = true;
                        }
                    }
                }
            }
        }

        BigDecimal totalGovDisc = actualTotalDiscount;

        // 4. 决定资金流向和回税逻辑 (VBN的含税规则优先于MGS的免税规则)
        BigDecimal totalVatable = BigDecimal.ZERO;
        BigDecimal totalVatExempt = BigDecimal.ZERO;
        BigDecimal totalVatAmount = BigDecimal.ZERO;

        // 最终税务状态：如果订单中包含VBN折扣，则整单按“含税”处理。
        boolean finalOrderIsExempt = !containsVBNDiscount && totalGovDisc.compareTo(BigDecimal.ZERO) > 0;

        if (finalOrderIsExempt) {
            // 场景：只有MGS折扣（或无折扣但代码逻辑走到这里），整单免税
            // 免税销售额 = 总净额 - 总折扣
            totalVatExempt = totalNet.subtract(totalGovDisc).setScale(2, RoundingMode.HALF_UP);
            totalVatAmount = BigDecimal.ZERO; // 免税模式下VAT为0

            if (discounts != null && !discounts.isEmpty()) {
                discounts.get(0).setLessVat(totalVat.setScale(2, RoundingMode.HALF_UP));
                discounts.get(0).setAddVat(BigDecimal.ZERO);
                discounts.get(0).setDiscount(totalGovDisc);
            }
        } else {
            // 场景：包含VBN折扣，或没有任何折扣，整单含税
            // 最终应付总额 = 总基础金额 - 总折扣
            BigDecimal finalGrossPayable = totalBaseGross.subtract(totalGovDisc);

            // 从最终应付总额中反算出最终的应税净额和VAT
            BigDecimal finalVatableNet = finalGrossPayable.divide(DiscountHelpers.VAT_DIVISOR, 2, RoundingMode.HALF_UP);
            BigDecimal finalVatAmount = finalGrossPayable.subtract(finalVatableNet);

            totalVatable = finalVatableNet;
            totalVatAmount = finalVatAmount;

            if (discounts != null && !discounts.isEmpty()) {
                discounts.get(0).setLessVat(totalVat.setScale(2, RoundingMode.HALF_UP));
                // 在含税模式下，AddVAT等于原始总VAT，表示税款被“加回”了
                discounts.get(0).setAddVat(totalVat.setScale(2, RoundingMode.HALF_UP));
                discounts.get(0).setDiscount(totalGovDisc);
            }
        }

        // 5. 填充订单总额
        DiscountHelpers.fillOrderTotals(order, totalGross, totalRegDisc, totalGovDisc,
                totalVatable,
                totalVatExempt,
                BigDecimal.ZERO,
                totalVatAmount);

        return scBasisAccumulator.setScale(2, RoundingMode.HALF_UP);
    }
}
