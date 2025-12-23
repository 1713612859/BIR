package com.zuno.bir.engine.core;

import com.zuno.bir.enums.BusinessType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计算器工厂
 * <p>
 * 根据业务类型创建对应的订单计算器。
 */
public class CalculatorFactory {
    
    private static final Map<BusinessType, OrderCalculator> calculatorCache = new ConcurrentHashMap<>();
    
    private static final Map<BusinessType, Class<? extends OrderCalculator>> calculatorClasses = new HashMap<>();
    
    static {
        calculatorClasses.put(BusinessType.RETAIL, com.zuno.bir.engine.core.impl.RetailOrderCalculator.class);
        calculatorClasses.put(BusinessType.DINING, com.zuno.bir.engine.core.impl.DiningOrderCalculator.class);
        calculatorClasses.put(BusinessType.FAST_FOOD, com.zuno.bir.engine.core.impl.FastFoodOrderCalculator.class);
    }
    
    /**
     * 获取订单计算器
     * 
     * @param businessType 业务类型
     * @return 订单计算器实例
     */
    public static OrderCalculator getCalculator(BusinessType businessType) {
        if (businessType == null) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        
        // 从缓存获取
        OrderCalculator calculator = calculatorCache.get(businessType);
        if (calculator != null) {
            return calculator;
        }
        
        // 创建新实例
        Class<? extends OrderCalculator> clazz = calculatorClasses.get(businessType);
        if (clazz == null) {
            throw new IllegalArgumentException("不支持的业务类型: " + businessType);
        }
        
        try {
            calculator = clazz.getDeclaredConstructor().newInstance();
            calculatorCache.put(businessType, calculator);
            return calculator;
        } catch (Exception e) {
            throw new RuntimeException("创建计算器失败: " + businessType, e);
        }
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        calculatorCache.clear();
    }
}

