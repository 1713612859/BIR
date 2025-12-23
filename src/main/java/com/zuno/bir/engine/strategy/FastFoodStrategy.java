package com.zuno.bir.engine.strategy;

import com.zuno.bir.engine.processor.DiscountProcessor;
import com.zuno.bir.engine.processor.FastFoodProcessor;
import com.zuno.bir.engine.processor.ItemLevelProcessor;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 快餐业务策略
 * <p>
 * 职责：为快餐业务类型提供折扣计算策略。
 * <p>
 * 业务规则：
 * - 快餐&奶茶支持SC、PWD、NAAC、MOV、DIPLOMATIC（不支持SP）
 * - 快餐&奶茶不支持整单折扣（按人数比例分摊）
 * <p>
 * 策略选择逻辑：
 * - 默认使用 FastFoodProcessor（整单折扣模式）
 * - 也可以使用 ItemLevelProcessor（逐项计算模式）
 * <p>
 * 快餐业务特点：
 * - 采用整单折扣模式，基于整个订单的总净额计算折扣
 * - 不支持VBN商品折扣
 * - 不支持SP（单亲父母）折扣
 * - 如果存在有效折扣，整单免税
 */
public class FastFoodStrategy implements BusinessStrategy {
    
    private final DiscountProcessor processor;
    
    /**
     * 构造函数
     * <p>
     * 默认使用整单折扣模式（FastFoodProcessor）
     * 如果需要逐项计算模式，可以传入 ItemLevelProcessor
     */
    public FastFoodStrategy() {
        // 默认使用整单折扣模式
        this.processor = new FastFoodProcessor();
        
        // 如果需要逐项计算模式，可以取消下面的注释：
        // this.processor = new ItemLevelProcessor();
    }
    
    /**
     * 构造函数 - 允许指定处理器
     * 
     * @param processor 折扣处理器
     */
    public FastFoodStrategy(DiscountProcessor processor) {
        this.processor = processor;
    }
    
    @Override
    public BusinessType getSupportedBusinessType() {
        return BusinessType.FAST_FOOD;
    }
    
    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, 
                               List<GovernmentDiscount> discounts, Store store) {
        return processor.calculate(order, items, discounts, store);
    }
    
    @Override
    public boolean isApplicable(SalesOrder order) {
        // 快餐业务策略适用于所有快餐订单
        return BusinessType.FAST_FOOD.name().equalsIgnoreCase(order.getBusinessType());
    }
}

