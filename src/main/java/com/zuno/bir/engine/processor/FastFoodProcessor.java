package com.zuno.bir.engine.processor;

import com.zuno.bir.engine.DiscountHelpers;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.ItemTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 快餐业务处理器 (FastFood Processor)
 * <p>
 * 职责：为快餐业务类型实现特定的折扣计算逻辑。
 * 核心逻辑：
 * 1. 采用“整单折扣”模式，即基于整个订单的总净额来计算折扣，而不是逐个商品计算。
 * 2. 快餐业态不支持对基本必需品(VBN)和单亲父母(SP)的折扣。
 * 3. 如果存在有效的折扣（如老年人、残疾人），则默认按20%的折扣率计算，并且整单免税。
 * 4. 如果折扣类型不被支持，则订单按正常应税处理，不应用任何政府折扣。
 */
public class FastFoodProcessor implements DiscountProcessor {

    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts, Store store) {

        BigDecimal totalGross = BigDecimal.ZERO;     // 总销售额
        BigDecimal totalRegDisc = BigDecimal.ZERO;   // 总常规折扣
        BigDecimal totalBaseGross = BigDecimal.ZERO; // 折扣计算基础总额 (Gross - Regular Discount)

        // 1. 计算整单的基础总额 (Base Gross)
        for(SalesOrderItem item : items) {
            BigDecimal g = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            BigDecimal r = item.getRegularDiscount() != null ? item.getRegularDiscount() : BigDecimal.ZERO;
            totalGross = totalGross.add(g);
            totalRegDisc = totalRegDisc.add(r);
            totalBaseGross = totalBaseGross.add(g.subtract(r));
        }

        // 2. 对整单基础总额进行去税，计算出整单的净额和税额
        BigDecimal totalNet = totalBaseGross.divide(DiscountHelpers.VAT_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal totalVat = totalBaseGross.subtract(totalNet);

        // 3. 初始化累加器
        BigDecimal totalGovDisc = BigDecimal.ZERO;     // 总政府折扣
        BigDecimal totalVatExempt = BigDecimal.ZERO;   // 总免税销售额
        BigDecimal totalVatable = BigDecimal.ZERO;     // 总应税销售额
        BigDecimal globalAddBackVat = BigDecimal.ZERO; // 需要加回的VAT总额
        BigDecimal scBasisAccumulator = totalNet;      // 服务费的计算基础是整单的总净额

        // 4. 快餐整单折扣逻辑 (只考虑列表中的第一个有效折扣)
        if(discounts != null && !discounts.isEmpty()) {
            GovernmentDiscount firstGd = discounts.get(0);

            // 默认按MGS商品类型（20%折扣，免税）来检查折扣资格
            // 因为快餐业态排除了VBN，所以这里假设所有商品都符合MGS的检查条件
            boolean isAllowedDiscountType = DiscountHelpers.isDiscountAllowed(
                    BusinessType.FAST_FOOD,
                    DiscountHelpers.parseDiscountType(firstGd.getPersonType()),
                    ItemTag.MGS
            );

            if(isAllowedDiscountType) {
                // 如果折扣类型被快餐业态支持 (例如 SC, PWD)
                BigDecimal rate = new BigDecimal("0.20"); // 默认20%折扣率

                // 计算折扣金额
                BigDecimal discAmt = totalNet.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                totalGovDisc = discAmt;

                // 因为是免税折扣，所以最终金额（净额 - 折扣）全部归入免税销售额
                totalVatExempt = totalNet.subtract(discAmt);

                // 设置用于小票显示的LessVAT/AddVAT
                firstGd.setLessVat(totalVat);      // 免去的VAT是整单的原始总VAT
                firstGd.setAddVat(BigDecimal.ZERO); // 因为整单免税，所以没有需要加回的VAT
            } else {
                // 如果折扣类型不被支持 (例如 SP)，则订单按无折扣处理
                totalVatable = totalNet;          // 全部净额归入应税销售额
                globalAddBackVat = totalVat;      // 全部原始VAT都需要加回

                // 相应地更新LessVAT/AddVAT
                firstGd.setLessVat(totalVat);
                firstGd.setAddVat(globalAddBackVat);
            }

            // 无论折扣是否为零，都更新折扣凭证上的金额
            firstGd.setDiscount(totalGovDisc);

        } else {
            // 如果没有任何政府折扣信息，则整单按正常应税处理
            totalVatable = totalNet;
            globalAddBackVat = totalVat;
        }

        // 5. 填充订单总额
        // 最终的VAT总额 = 需要加回的VAT总额
        BigDecimal totalVatAmount = globalAddBackVat.setScale(2, RoundingMode.HALF_UP);
        DiscountHelpers.fillOrderTotals(order, totalGross, totalRegDisc, totalGovDisc, totalVatable, totalVatExempt, BigDecimal.ZERO, totalVatAmount);

        return scBasisAccumulator;
    }
}
