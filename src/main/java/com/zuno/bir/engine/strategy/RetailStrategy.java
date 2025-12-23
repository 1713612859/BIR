package com.zuno.bir.engine.strategy;

import com.zuno.bir.engine.processor.DiscountProcessor;
import com.zuno.bir.engine.processor.ItemLevelProcessor;
import com.zuno.bir.engine.processor.RetailWholeOrderProcessor;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 零售业务策略
 * <p>
 * 职责：为零售业务类型提供折扣计算策略。
 * <p>
 * 业务规则：
 * - 零售支持SC、PWD、NAAC、SP、MOV、DIPLOMATIC
 * - 零售不支持整单折扣（按人数比例分摊）
 * <p>
 * 策略选择逻辑：
 * - 默认使用 ItemLevelProcessor（逐项计算模式）
 * - 如果需要整单折扣模式，可以使用 RetailWholeOrderProcessor
 * <p>
 * 零售业务特点：
 * - 支持MGS和VBN商品的折扣
 * - 支持SC、PWD、NAAC、MOV、SP等折扣类型
 * - VBN商品折扣会导致整单含税，MGS商品折扣会导致免税
 */
public class RetailStrategy implements BusinessStrategy {
    
    private final DiscountProcessor processor;
    
    /**
     * 构造函数
     * <p>
     * 默认使用逐项计算模式（ItemLevelProcessor）
     * 如果需要整单折扣模式，可以传入 RetailWholeOrderProcessor
     */
    public RetailStrategy() {
        // 默认使用逐项计算模式
        this.processor = new ItemLevelProcessor();
        
        // 如果需要整单折扣模式，可以取消下面的注释：
        // this.processor = new RetailWholeOrderProcessor();
    }
    
    /**
     * 构造函数 - 允许指定处理器
     * 
     * @param processor 折扣处理器
     */
    public RetailStrategy(DiscountProcessor processor) {
        this.processor = processor;
    }
    
    @Override
    public BusinessType getSupportedBusinessType() {
        return BusinessType.RETAIL;
    }
    
    @Override
    public BigDecimal calculate(SalesOrder order, List<SalesOrderItem> items, 
                               List<GovernmentDiscount> discounts, Store store) {
        return processor.calculate(order, items, discounts, store);
    }
    
    @Override
    public boolean isApplicable(SalesOrder order) {
        // 零售业务策略适用于所有零售订单
        return BusinessType.RETAIL.name().equalsIgnoreCase(order.getBusinessType());
    }
}

