package com.zuno.bir.engine.core;

import com.zuno.bir.entity.*;
import com.zuno.bir.enums.BusinessType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 计算上下文
 * <p>
 * 封装订单计算所需的所有数据，提供清晰的数据结构。
 */
@Data
@Builder
public class CalculationContext {
    /**
     * 销售订单
     */
    private SalesOrder order;

    /**
     * 订单商品项列表
     */
    private List<SalesOrderItem> items;

    /**
     * 政府折扣列表
     */
    private List<GovernmentDiscount> governmentDiscounts;

    /**
     * 店铺信息
     */
    private Store store;


    /**
     * 终端信息
     */
    private Device device;

    /**
     * 收银员信息
     */
    private User cashier;

    /**
     * 支付信息列表
     */
    private List<OrderPayment> payments;

    /**
     * 业务类型
     */
    private BusinessType businessType;

    /**
     * 整单折扣金额（非政府折扣，用于零售业态）
     */
    private java.math.BigDecimal wholeOrderDiscountAmount;

    /**
     * 整单折扣百分比（非政府折扣，用于零售业态）
     */
    private java.math.BigDecimal wholeOrderDiscountPercent;
}

