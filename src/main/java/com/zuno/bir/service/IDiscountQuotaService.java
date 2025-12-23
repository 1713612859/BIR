package com.zuno.bir.service;

import com.zuno.bir.entity.DiscountQuota;
import com.zuno.bir.entity.SalesOrder;

import java.util.List;

/**
 * 折扣配额服务接口
 */
public interface IDiscountQuotaService {

    /**
     * 获取订单的折扣配额信息
     *
     * @param order 销售订单
     * @return 折扣配额列表（可能包含多个折扣类型的配额）
     */
    List<DiscountQuota> getDiscountQuotas(SalesOrder order);

    /**
     * 更新折扣配额（订单完成后调用）
     *
     * @param order 销售订单
     */
    void updateDiscountQuotas(SalesOrder order);
}

