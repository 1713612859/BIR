package com.zuno.bir.engine.strategy;

import com.zuno.bir.engine.processor.DiningProcessor;
import com.zuno.bir.engine.processor.ItemLevelProcessor;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 餐饮业务策略
 * <p>
 * 职责：为餐饮业务类型提供折扣计算策略。
 * <p>
 * 业务规则：
 * - 餐饮支持SC、PWD、NAAC、MOV、DIPLOMATIC（不支持SP）
 * - 只有餐饮支持整单折扣（按人数比例分摊）
 * - 整单折扣支持：SC、PWD、NAAC、MOV（20%折扣率）
 * - 整单折扣不支持：SP、DIPLOMATIC
 * - DIPLOMATIC和其他折扣互斥，不支持整单折扣
 * <p>
 * 策略选择逻辑：
 * - 如果是团体餐（Group Meal）且不存在DIPLOMATIC折扣，使用 DiningProcessor（按人头分摊折扣）
 * - 如果是单点模式或存在DIPLOMATIC折扣，使用 ItemLevelProcessor（逐项计算）
 * <p>
 * 餐饮业务特点：
 * - 支持团体餐折扣分摊（按人数均分）
 * - 支持服务费计算
 * - 不支持VBN商品折扣
 * - 不支持SP（单亲父母）折扣
 */
public class DiningStrategy implements BusinessStrategy {
    
    private final DiningProcessor diningProcessor;
    private final ItemLevelProcessor itemLevelProcessor;
    
    public DiningStrategy() {
        this.diningProcessor = new DiningProcessor();
        this.itemLevelProcessor = new ItemLevelProcessor();
    }
    
    @Override
    public BusinessType getSupportedBusinessType() {
        return BusinessType.DINING;
    }
    
    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, 
                               List<GovernmentDiscount> discounts, Store store) {
        // 根据是否为团体餐选择不同的处理器
        if (order.isGroupMeal()) {
            // 团体餐模式：按人头分摊折扣
            return diningProcessor.calculate(order, items, discounts, store);
        } else {
            // 单点模式：逐项计算折扣
            return itemLevelProcessor.calculate(order, items, discounts, store);
        }
    }
    
    @Override
    public boolean isApplicable(SalesOrder order) {
        // 餐饮业务策略适用于所有餐饮订单
        return BusinessType.DINING.name().equalsIgnoreCase(order.getBusinessType());
    }
}

