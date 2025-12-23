package com.zuno.bir.engine.strategy;

import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.enums.BusinessType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【已废弃】旧版业务策略工厂
 * <p>
 * <b>⚠️ 注意：此类已被 {@link com.zuno.bir.engine.core.CalculatorFactory} 替代。</b>
 * <p>
 * <b>迁移指南：</b>
 * 请使用新的架构：
 * <ul>
 *   <li>使用 {@link com.zuno.bir.engine.core.CalculatorFactory#getCalculator(BusinessType)} 替代此工厂</li>
 *   <li>使用 {@link com.zuno.bir.engine.core.OrderCalculator} 替代 {@link BusinessStrategy}</li>
 * </ul>
 * <p>
 * <b>保留原因：</b>
 * 为了向后兼容，暂时保留此类。部分旧代码可能仍在使用。
 * 
 * @deprecated 请使用 {@link com.zuno.bir.engine.core.CalculatorFactory} 替代
 */
@Deprecated
public class BusinessStrategyFactory {
    
    /**
     * 策略实例缓存
     * 使用ConcurrentHashMap保证线程安全
     */
    private static final Map<BusinessType, BusinessStrategy> strategyCache = new ConcurrentHashMap<>();
    
    /**
     * 默认策略映射
     * 为每个业务类型注册默认策略
     */
    private static final Map<BusinessType, Class<? extends BusinessStrategy>> defaultStrategies = new HashMap<>();
    
    static {
        // 注册默认策略
        defaultStrategies.put(BusinessType.RETAIL, RetailStrategy.class);
        defaultStrategies.put(BusinessType.DINING, DiningStrategy.class);
        defaultStrategies.put(BusinessType.FAST_FOOD, FastFoodStrategy.class);
    }
    
    /**
     * 根据订单获取对应的业务策略
     * <p>
     * 首先尝试从缓存中获取，如果不存在则创建新实例并缓存。
     * 
     * @param order 销售订单对象
     * @return 对应的业务策略实例，如果业务类型不支持则返回null
     */
    public static BusinessStrategy getStrategy(SalesOrder order) {
        if (order == null || order.getBusinessType() == null) {
            return null;
        }
        
        BusinessType bizType = parseBusinessType(order.getBusinessType());
        if (bizType == null) {
            return null;
        }
        
        // 从缓存中获取策略
        BusinessStrategy strategy = strategyCache.get(bizType);
        if (strategy != null) {
            return strategy;
        }
        
        // 创建新策略实例
        strategy = createStrategy(bizType);
        if (strategy != null) {
            strategyCache.put(bizType, strategy);
        }
        
        return strategy;
    }
    
    /**
     * 根据业务类型获取对应的业务策略
     * 
     * @param businessType 业务类型枚举
     * @return 对应的业务策略实例，如果业务类型不支持则返回null
     */
    public static BusinessStrategy getStrategy(BusinessType businessType) {
        if (businessType == null) {
            return null;
        }
        
        // 从缓存中获取策略
        BusinessStrategy strategy = strategyCache.get(businessType);
        if (strategy != null) {
            return strategy;
        }
        
        // 创建新策略实例
        strategy = createStrategy(businessType);
        if (strategy != null) {
            strategyCache.put(businessType, strategy);
        }
        
        return strategy;
    }
    
    /**
     * 创建策略实例
     * 
     * @param businessType 业务类型
     * @return 策略实例
     */
    private static BusinessStrategy createStrategy(BusinessType businessType) {
        Class<? extends BusinessStrategy> strategyClass = defaultStrategies.get(businessType);
        if (strategyClass == null) {
            return null;
        }
        
        try {
            return strategyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create strategy for business type: " + businessType, e);
        }
    }
    
    /**
     * 注册自定义策略
     * <p>
     * 允许在运行时注册或替换特定业务类型的策略实现。
     * 注意：此操作会清除该业务类型的缓存，下次获取时会使用新策略。
     * 
     * @param businessType 业务类型
     * @param strategy 策略实例
     */
    public static void registerStrategy(BusinessType businessType, BusinessStrategy strategy) {
        if (businessType != null && strategy != null) {
            strategyCache.put(businessType, strategy);
        }
    }
    
    /**
     * 清除策略缓存
     * <p>
     * 下次获取策略时会重新创建实例。
     */
    public static void clearCache() {
        strategyCache.clear();
    }
    
    /**
     * 解析业务类型字符串
     * 
     * @param businessTypeStr 业务类型字符串
     * @return 业务类型枚举，解析失败返回null
     */
    private static BusinessType parseBusinessType(String businessTypeStr) {
        if (businessTypeStr == null || businessTypeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return BusinessType.valueOf(businessTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

