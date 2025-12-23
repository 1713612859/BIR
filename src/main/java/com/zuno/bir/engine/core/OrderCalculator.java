package com.zuno.bir.engine.core;

/**
 * 【核心接口】订单计算器接口
 * <p>
 * <b>设计目的：</b>
 * 定义统一的订单计算接口，实现策略模式，使不同业态（RETAIL、DINING、FAST_FOOD）
 * 可以有不同的计算逻辑实现。
 * <p>
 * <b>实现类：</b>
 * <ul>
 *   <li>{@link com.zuno.bir.engine.core.impl.RetailOrderCalculator} - 零售业态计算器</li>
 *   <li>{@link com.zuno.bir.engine.core.impl.DiningOrderCalculator} - 餐饮业态计算器</li>
 *   <li>{@link com.zuno.bir.engine.core.impl.FastFoodOrderCalculator} - 快餐业态计算器</li>
 * </ul>
 * <p>
 * <b>计算职责：</b>
 * 每个实现类负责：
 * <ol>
 *   <li>根据业务规则判断计算模式（政府折扣/整单折扣/无折扣）</li>
 *   <li>计算商品折扣、税费、最终金额</li>
 *   <li>将计算结果更新到 {@link CalculationContext} 中的订单和商品项对象</li>
 *   <li>返回 {@link CalculationResult} 包含汇总的金额信息</li>
 * </ol>
 * <p>
 * <b>使用方式：</b>
 * 通过 {@link CalculatorFactory#getCalculator(BusinessType)} 获取对应的计算器实例，
 * 然后调用 {@link #calculate(CalculationContext)} 方法执行计算。
 */
public interface OrderCalculator {
    
    /**
     * 计算订单
     * <p>
     * 根据计算上下文，计算订单的所有金额、折扣、税费等信息。
     * 计算结果会直接更新到context中的order和items对象中。
     * 
     * @param context 计算上下文，包含订单、商品项、折扣等信息
     * @return 计算结果
     */
    CalculationResult calculate(CalculationContext context);
    
    /**
     * 获取支持的业务类型
     * 
     * @return 业务类型
     */
    com.zuno.bir.enums.BusinessType getSupportedBusinessType();
}

