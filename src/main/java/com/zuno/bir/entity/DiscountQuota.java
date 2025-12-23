package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 折扣配额实体类
 * <p>
 * 用于记录零售业态下MGS/VBN折扣的周配额信息。
 */
@Data
@TableName("discount_quota")
public class DiscountQuota {
    /**
     * 配额ID，主键
     */
    @TableId
    private Long quotaId;
    
    /**
     * 客户TIN（用于识别客户）
     */
    private String customerTin;
    
    /**
     * 折扣类型（SC、PWD等）
     */
    private String discountType;
    
    /**
     * 商品标签（MGS、VBN）
     */
    private String itemTag;
    
    /**
     * 周开始日期（用于计算周配额）
     */
    private Date weekStartDate;
    
    /**
     * 之前的折扣金额（本周之前已使用的折扣）
     */
    private BigDecimal previousDiscountAmount;
    
    /**
     * 当前折扣金额（本次订单使用的折扣）
     */
    private BigDecimal currentDiscountAmount;
    
    /**
     * 剩余周配额（本周剩余可用的折扣额度）
     */
    private BigDecimal remainingWeeklyQuota;
    
    /**
     * 周配额上限（每周最大折扣额度）
     */
    private BigDecimal weeklyQuotaLimit;
}

