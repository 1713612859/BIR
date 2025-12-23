package com.zuno.bir.engine.processor;

import com.zuno.bir.engine.DiscountHelpers;
import com.zuno.bir.engine.ItemLevelCalculator;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 逐项商品处理器 (Item-Level Processor) - 职责分离版
 * <p>
 * 职责：
 * 1. 协调对订单中每一个商品项进行独立计算。
 * 2. 在所有商品计算完毕后，根据最终的税务总额计算服务费基础。
 */
public class ItemLevelProcessor implements DiscountProcessor {

    private static final int FINAL_SCALE = 2;

    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts, Store store) {

        // 1. 初始化
        DiscountHelpers.resetTotals(order);
        if (discounts != null) {
            DiscountHelpers.resetDiscountAmounts(discounts);
        }

        BusinessType currentBizType = DiscountHelpers.parseBusinessType(order.getBusinessType());
        ItemLevelCalculator.DiscountAggregator aggregator = new ItemLevelCalculator.DiscountAggregator();

        // 2. 逐行计算
        for (SalesOrderItem item : items) {
            ItemLevelCalculator.processItem(order, item, discounts, currentBizType, aggregator);
        }

        // 3. 填充小票显示的 "Less VAT" / "Add VAT"
        if (discounts != null && !discounts.isEmpty()) {
            GovernmentDiscount first = discounts.get(0);
            first.setLessVat(aggregator.displayLessVat.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
            first.setAddVat(BigDecimal.ZERO);
        }

        // 4. 填充订单最终总额
        DiscountHelpers.fillOrderTotals(order,
                aggregator.totalGross,
                aggregator.totalRegDisc,
                aggregator.totalGovDisc,
                aggregator.totalVatable.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                aggregator.totalVatExempt.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                aggregator.totalZeroRated.setScale(FINAL_SCALE, RoundingMode.HALF_UP),
                aggregator.totalVatAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP));

        // 5. 【核心修正】计算并返回服务费基础 (SC Basis)
        // 根据规则“服务费不能是 /1.12 后的数值”，服务费基础应为最终的含税总额。
        // 最终含税总额 = 应税部分(Vatable + VAT) + 免税部分(Exempt) + 零税率部分(Zero-Rated)
        BigDecimal scBasis = aggregator.totalVatable
                .add(aggregator.totalVatAmount)
                .add(aggregator.totalVatExempt)
                .add(aggregator.totalZeroRated);

        return scBasis.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
    }
}
