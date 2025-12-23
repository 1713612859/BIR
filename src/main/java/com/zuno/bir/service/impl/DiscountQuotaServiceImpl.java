package com.zuno.bir.service.impl;

import com.zuno.bir.entity.DiscountQuota;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.enums.DiscountType;
import com.zuno.bir.enums.ItemTag;
import com.zuno.bir.service.IDiscountQuotaService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 折扣配额服务实现类
 * <p>
 * 注意：这是一个简化实现，实际项目中需要从数据库或外部系统获取配额信息。
 */
@Service
public class DiscountQuotaServiceImpl implements IDiscountQuotaService {
    
    // 周配额上限（示例值，实际应从配置或数据库获取）
    private static final BigDecimal WEEKLY_QUOTA_LIMIT_MGS = new BigDecimal("2000.00"); // MGS商品周配额上限
    private static final BigDecimal WEEKLY_QUOTA_LIMIT_VBN = new BigDecimal("1000.00"); // VBN商品周配额上限
    
    @Override
    public List<DiscountQuota> getDiscountQuotas(SalesOrder order) {
        List<DiscountQuota> quotas = new ArrayList<>();
        
        // 只处理零售业态
        if (!"RETAIL".equalsIgnoreCase(order.getBusinessType())) {
            return quotas;
        }
        
        // 只处理有政府折扣的订单
        if (order.getGovDiscounts() == null || order.getGovDiscounts().isEmpty()) {
            return quotas;
        }
        
        // 获取订单中的折扣信息
        for (GovernmentDiscount gd : order.getGovDiscounts()) {
            DiscountType discountType = com.zuno.bir.engine.DiscountHelpers.parseDiscountType(gd.getPersonType());
            
            // 只处理SC和PWD（MGS和VBN折扣）
            if (discountType != DiscountType.SC && discountType != DiscountType.PWD) {
                continue;
            }
            
            // 检查订单中的商品标签
            if (order.getItems() != null) {
                for (com.zuno.bir.entity.SalesOrderItem item : order.getItems()) {
                    ItemTag itemTag = com.zuno.bir.engine.DiscountHelpers.parseItemTag(item.getDiscountTag());
                    
                    // 只处理MGS和VBN商品
                    if (itemTag != ItemTag.MGS && itemTag != ItemTag.VBN) {
                        continue;
                    }
                    
                    // 创建配额对象
                    DiscountQuota quota = new DiscountQuota();
                    quota.setCustomerTin(order.getCustomerTin());
                    quota.setDiscountType(gd.getPersonType());
                    quota.setItemTag(itemTag.name());
                    quota.setWeekStartDate(getWeekStartDate(new Date()));
                    
                    // 计算当前折扣金额
                    BigDecimal currentDiscount = gd.getDiscount() != null ? gd.getDiscount() : BigDecimal.ZERO;
                    quota.setCurrentDiscountAmount(currentDiscount);
                    
                    // 获取之前的折扣金额（从数据库或缓存，这里简化处理）
                    BigDecimal previousDiscount = getPreviousDiscountAmount(
                            order.getCustomerTin(), 
                            gd.getPersonType(), 
                            itemTag.name(),
                            quota.getWeekStartDate());
                    quota.setPreviousDiscountAmount(previousDiscount);
                    
                    // 计算剩余配额
                    BigDecimal quotaLimit = itemTag == ItemTag.MGS ? 
                            WEEKLY_QUOTA_LIMIT_MGS : WEEKLY_QUOTA_LIMIT_VBN;
                    BigDecimal remaining = quotaLimit.subtract(previousDiscount).subtract(currentDiscount);
                    quota.setRemainingWeeklyQuota(remaining.max(BigDecimal.ZERO));
                    quota.setWeeklyQuotaLimit(quotaLimit);
                    
                    quotas.add(quota);
                }
            }
        }
        
        return quotas;
    }
    
    @Override
    public void updateDiscountQuotas(SalesOrder order) {
        // 更新折扣配额到数据库或外部系统
        // 这里简化处理，实际需要调用DAO或外部API
        List<DiscountQuota> quotas = getDiscountQuotas(order);
        // TODO: 保存到数据库或更新外部系统
    }
    
    /**
     * 获取周开始日期
     */
    private Date getWeekStartDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
    
    /**
     * 获取之前的折扣金额（从数据库或缓存）
     * 这里简化处理，实际需要查询数据库
     */
    private BigDecimal getPreviousDiscountAmount(String customerTin, String discountType, 
                                                 String itemTag, Date weekStart) {
        // TODO: 从数据库查询本周之前的折扣金额
        // 这里返回0作为示例
        return BigDecimal.ZERO;
    }
}

