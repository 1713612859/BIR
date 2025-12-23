package com.zuno.bir.service;

import com.zuno.bir.dto.OrderPreviewResponse;
import com.zuno.bir.entity.SalesOrder;

/**
 * 销售订单服务接口
 * <p>
 * 定义了与销售订单相关的业务操作。
 * 业务逻辑的实现位于 {@link com.zuno.bir.service.impl.SalesOrderServiceImpl} 类中。
 */
public interface ISalesOrderService {

    /**
     * 创建一个新的销售订单。
     * <p>
     * 这个过程通常包括：
     * 1. 对订单数据进行业务校验。
     * 2. 调用折扣引擎计算所有金额和税费。
     * 3. 将订单、订单项、折扣等信息持久化到数据库。
     *
     * @param order 包含前端传入的原始订单信息的 {@link SalesOrder} 对象。
     * @return 经过计算和持久化后，包含完整信息的 {@link SalesOrder} 对象。
     */
    SalesOrder createOrder(SalesOrder order);

    /**
     * 根据订单ID获取订单的完整信息。
     * <p>
     * 这通常需要关联查询订单的商品项 (items)、折扣信息 (govDiscounts) 和支付信息 (payments)。
     *
     * @param orderId 要查询的订单ID。
     * @return 包含所有关联信息的 {@link SalesOrder} 对象，如果找不到则返回null。
     */
    SalesOrder getOrderWithItems(Long orderId);

    /**
     * 订单实时预览（不落库）
     * <p>
     * 用于前端在编辑订单时实时查看政府折扣计算结果和发票内容。
     *
     * @param order 前端传入的临时订单（包含 items / govDiscounts / payments 等）
     * @return 计算后的订单 + 对应的小票文本
     */
    OrderPreviewResponse previewOrder(SalesOrder order);
}
