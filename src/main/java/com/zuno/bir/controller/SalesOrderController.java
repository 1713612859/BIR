package com.zuno.bir.controller;

import com.zuno.bir.dto.OrderPreviewResponse;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.service.ISalesOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class SalesOrderController {

    @Autowired
    private ISalesOrderService salesOrderService;

    /**
     * 创建新订单并进行计算
     * @param order 前端发送的订单数据
     * @return 计算完成后的订单详情
     */
    @PostMapping
    public SalesOrder createOrder(@RequestBody SalesOrder order) {
        // 这里假设前端发送的 order 对象已经包含了 items, govDiscounts, payments 等信息
        // 调用 service 层执行核心业务逻辑
        return salesOrderService.createOrder(order);
    }

    /**
     * 根据ID获取订单详情
     * @param orderId 订单ID
     * @return 订单的完整信息
     */
    @GetMapping("/{orderId}")
    public SalesOrder getOrderById(@PathVariable Long orderId) {
        return salesOrderService.getOrderWithItems(orderId);
    }

    /**
     * 订单实时预览接口（不落库）
     * <p>
     * 前端在编辑商品和政府折扣时，可以调用该接口实时获取：
     * - 计算后的订单金额/税费/折扣
     * - 生成好的小票文本（便于在页面上可视化预览）
     */
    @PostMapping("/preview")
    public OrderPreviewResponse previewOrder(@RequestBody SalesOrder order) {
        return salesOrderService.previewOrder(order);
    }
}
