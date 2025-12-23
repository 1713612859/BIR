package com.zuno.bir.engine;

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
 * 【核心逻辑】逐项商品计算器 (Item-Level Calculator)
 * <p>
 * <b>核心职责：</b>
 * 负责计算单个商品项的折扣、税费和最终金额。这是整个折扣计算系统的核心基础组件。
 * <p>
 * <b>计算流程：</b>
 * <ol>
 *   <li><b>基础金额准备</b>：计算商品总价（单价×数量）和常规折扣后的基础金额</li>
 *   <li><b>原始净价/税额计算</b>：根据商品VAT类型计算原始净价和税额</li>
 *   <li><b>折扣计算</b>：
 *       <ul>
 *         <li>查找适用的政府折扣（优先精确匹配商品ID，其次全局折扣）</li>
 *         <li>验证折扣是否允许（根据业务类型、折扣类型、商品标签）</li>
 *         <li>计算折扣金额（基于折扣率和原始净价）</li>
 *         <li>判断是否免税（MGS、SP、NAAC、MOV、DIPLOMATIC商品享受免税）</li>
 *       </ul>
 *   </li>
 *   <li><b>最终金额计算</b>：基础金额 - 税额减免 - 折扣金额 = 最终支付金额</li>
 *   <li><b>税务归集（Tax Bucketing）</b>：
 *       <ul>
 *         <li>如果免税：归集到VAT_EXEMPT或ZERO_RATED</li>
 *         <li>如果含税：从最终金额反算Vatable和VAT金额</li>
 *       </ul>
 *   </li>
 * </ol>
 * <p>
 * <b>关键业务规则：</b>
 * <ul>
 *   <li>MGS商品：20%折扣 + 免税（适用于SC、PWD、NAAC、MOV）</li>
 *   <li>VBN商品：5%折扣 + 含税（仅零售，适用于SC、PWD）</li>
 *   <li>SP商品：10%折扣 + 免税（仅零售）</li>
 *   <li>NAAC商品：20%折扣（仅零售）</li>
 *   <li>MOV商品：20%折扣（仅零售）</li>
 *   <li>DIPLOMATIC：0%折扣 + 免税（所有业态，仅对含税商品生效）</li>
 * </ul>
 * <p>
 * <b>使用场景：</b>
 * 被 {@link com.zuno.bir.engine.core.impl.RetailOrderCalculator} 和
 * {@link com.zuno.bir.engine.core.impl.DiningOrderCalculator} 等业态计算器调用，
 * 用于逐项计算商品折扣和税费。
 */
public class ItemLevelCalculator {

    private static final int CALC_SCALE = 4;
    private static final int FINAL_SCALE = 2;

    public static void processItem(
            SalesOrder order,
            SalesOrderItem item,
            List<GovernmentDiscount> discounts,
            BusinessType currentBizType,
            DiscountAggregator aggregator) {

        // --- 1. 基础金额准备 ---
        BigDecimal itemGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
        BigDecimal regDisc = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
        BigDecimal baseGross = itemGross.subtract(regDisc).max(BigDecimal.ZERO);

        aggregator.totalGross = aggregator.totalGross.add(itemGross);
        aggregator.totalRegDisc = aggregator.totalRegDisc.add(regDisc);

        // --- 2. 原始净价/税额计算 ---
        String itemVatType = item.getVatType() != null ? item.getVatType() : "VATABLE";
        boolean originallyTaxable = !"VAT_EXEMPT".equalsIgnoreCase(itemVatType)
                && !"ZERO_RATED".equalsIgnoreCase(itemVatType);
        BigDecimal originalNet = DiscountHelpers.calculateNetPrice(baseGross, itemVatType, CALC_SCALE);
        BigDecimal originalVat = DiscountHelpers.calculateVatAmount(baseGross, itemVatType, CALC_SCALE);

        // --- 3. 折扣计算 ---
        // 不同业态下，单品折扣的“匹配范围”不同：
        // - RETAIL：允许整单级的政府折扣（itemId 为空时按规则匹配所有符合条件的商品）
        // - DINING / FAST_FOOD：只允许“点名”的单品折扣——即 GovernmentDiscount.itemId == 当前行 itemId
        GovernmentDiscount matchedGov = null;
        if (discounts != null && !discounts.isEmpty()) {
            if (currentBizType == BusinessType.RETAIL) {
                // 保持零售业态原有行为（支持全单政府折扣）
                matchedGov = DiscountHelpers.findDiscountForItem(discounts, item.getItemId());
            } else {
                // 餐饮 / 快餐业态：单品折扣只作用于被显式绑定的商品行
                Long currentItemId = item.getItemId();
                if (currentItemId != null) {
                    for (GovernmentDiscount gd : discounts) {
                        if (gd.getItemId() != null && gd.getItemId().equals(currentItemId)) {
                            matchedGov = gd;
                            break;
                        }
                    }
                }
            }
        }
        BigDecimal discountAmount = BigDecimal.ZERO;
        boolean isExemptFromVat = false;
        boolean govApplied = false; // 标记该行是否真正应用了政府折扣

        ItemTag itemTag = DiscountHelpers.parseItemTag(item.getDiscountTag());
        if (matchedGov != null) {
            DiscountType discType = DiscountHelpers.parseDiscountType(matchedGov.getPersonType());

            if (DiscountHelpers.isDiscountAllowed(currentBizType, discType, itemTag)) {
                BigDecimal rate = DiscountHelpers.getDiscountRate(discType, itemTag);
                discountAmount = originalNet.multiply(rate).setScale(FINAL_SCALE, RoundingMode.HALF_UP);

                if (discType == DiscountType.DIPLOMATIC || itemTag == ItemTag.MGS || itemTag == ItemTag.SP ||
                        itemTag == ItemTag.NAAC || itemTag == ItemTag.MOV) {
                    isExemptFromVat = true;
                }
                matchedGov.setDiscount(matchedGov.getDiscount().add(discountAmount));
                item.setAppliedDiscountType(discType.name());
                // 只要有折扣金额或去税，就认为本行参与了政府折扣
                govApplied = discountAmount.compareTo(BigDecimal.ZERO) > 0 || isExemptFromVat;
            }
        }
        
        // --- 4. 计算最终含税金额 (finalItemAmount) ---
        BigDecimal itemTaxReduction = BigDecimal.ZERO;
        boolean isNaturallyExempt = "VAT_EXEMPT".equalsIgnoreCase(itemVatType)
                || "ZERO_RATED".equalsIgnoreCase(itemVatType);

        if (!isNaturallyExempt && isExemptFromVat) {
            // 对于真正免税的场景（MGS/SP/NAAC/MOV/DIPLOMATIC 等），先把原始 VAT 完全去掉
            itemTaxReduction = originalVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        }

        // item.amount 代表【含税】的最终支付金额
        BigDecimal finalItemAmount = baseGross
                .subtract(itemTaxReduction)
                .subtract(discountAmount)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        item.setAmount(finalItemAmount);

        // --- 5. 累加总政府折扣 ---
        aggregator.totalGovDisc = aggregator.totalGovDisc.add(discountAmount).add(itemTaxReduction);

        // --- 6. 展示用 Less VAT 逻辑 ---
        // 只有“真正应用了政府折扣”的应税商品才参与 Less VAT 累计
        if (govApplied && originallyTaxable) {
            aggregator.displayLessVat = aggregator.displayLessVat.add(originalVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
        }

        // --- 7. 税务归集 (Tax Bucketing) ---
        if (isNaturallyExempt || isExemptFromVat) {
            if ("ZERO_RATED".equalsIgnoreCase(itemVatType)) {
                item.setZeroRatedSales(finalItemAmount);
                aggregator.totalZeroRated = aggregator.totalZeroRated.add(finalItemAmount);
            } else {
                item.setVatExemptSales(finalItemAmount);
                aggregator.totalVatExempt = aggregator.totalVatExempt.add(finalItemAmount);
            }
        } else {
            // 应税场景
            BigDecimal finalVatable;
            BigDecimal finalVat;

            if (itemTag == ItemTag.VBN && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                // VBN 特殊规则：
                // - 基于未税价(originalNet) 打 5% 折扣
                // - VAT 不变，仍按原始含税价计算（即 112 上的 VAT 仍为 12）
                finalVatable = originalNet.subtract(discountAmount).setScale(FINAL_SCALE, RoundingMode.HALF_UP);
                finalVat = originalVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
            } else {
                // 默认：从最终含税金额中反算 Vatable 和 VAT
                finalVatable = DiscountHelpers.calculateNetPrice(finalItemAmount, "VATABLE", FINAL_SCALE);
                finalVat = finalItemAmount.subtract(finalVatable);
            }

            item.setVatableSales(finalVatable);
            item.setVatAmount(finalVat);
            aggregator.totalVatable = aggregator.totalVatable.add(finalVatable);
            aggregator.totalVatAmount = aggregator.totalVatAmount.add(finalVat);

            // 只有真正应用了政府折扣的应税商品，其 VAT 才参与 Add 12% VAT 的汇总
            if (govApplied && originallyTaxable) {
                aggregator.globalAddBackVat = aggregator.globalAddBackVat.add(finalVat);
            }
        }
    }

    /**
     * 折扣计算聚合器 - 移除了 scBasisAccumulator
     */
    public static class DiscountAggregator {
        public BigDecimal totalGross = BigDecimal.ZERO;
        public BigDecimal totalRegDisc = BigDecimal.ZERO;
        public BigDecimal totalGovDisc = BigDecimal.ZERO;
        public BigDecimal totalVatable = BigDecimal.ZERO;
        public BigDecimal totalVatExempt = BigDecimal.ZERO;
        public BigDecimal totalVatAmount = BigDecimal.ZERO;
        public BigDecimal totalZeroRated = BigDecimal.ZERO;
        // public BigDecimal scBasisAccumulator = BigDecimal.ZERO; // REMOVED
        public BigDecimal displayLessVat = BigDecimal.ZERO;
        public BigDecimal globalAddBackVat = BigDecimal.ZERO;
    }
}
