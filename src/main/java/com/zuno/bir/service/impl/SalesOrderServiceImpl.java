package com.zuno.bir.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zuno.bir.dto.OrderPreviewResponse;
import com.zuno.bir.engine.core.NewDiscountEngine;
import com.zuno.bir.entity.*;
import com.zuno.bir.mapper.GovernmentDiscountMapper;
import com.zuno.bir.mapper.OrderPaymentMapper;
import com.zuno.bir.mapper.SalesOrderItemMapper;
import com.zuno.bir.mapper.SalesOrderMapper;
import com.zuno.bir.service.ISalesOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 销售订单服务实现类
 * <p>
 * 实现了 {@link ISalesOrderService} 接口中定义的业务方法。
 * 使用 Spring 的 {@link Service} 注解将其声明为服务层组件。
 */
@Service
public class SalesOrderServiceImpl implements ISalesOrderService {

    // 自动注入Mybatis-Plus的Mapper接口，用于数据库操作
    @Autowired
    private SalesOrderMapper orderMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private GovernmentDiscountMapper govMapper;
    @Autowired
    private OrderPaymentMapper paymentMapper;

    /**
     * 创建并持久化一个完整的销售订单。
     * <p>
     * 使用 {@link Transactional} 注解确保整个方法在一个数据库事务中执行，
     * 任何一步失败都会导致所有已执行的数据库操作回滚，保证数据一致性。
     * <p>
     * 注意：当前实现中，折扣计算是在数据插入之后执行的，这可能不是最佳实践。
     * 通常，计算应该在插入之前完成，以确保存入数据库的是最终的正确数据。
     *
     * @param order 包含订单、商品项、折扣和支付信息的订单对象。
     * @return 经过处理和持久化的订单对象。
     */
    @Override
    @Transactional
    public SalesOrder createOrder(SalesOrder order) {
        // 1. 保存主订单信息，Mybatis-Plus会自动将生成的主键回填到order对象中
        orderMapper.insert(order);

        // 2. 保存订单的商品项列表
        if (order.getItems() != null) {
            for (SalesOrderItem item : order.getItems()) {
                item.setOrderId(order.getOrderId()); // 关联主订单ID
                itemMapper.insert(item);
            }
        }

        // 3. 保存政府折扣信息
        if (order.getGovDiscounts() != null) {
            for (GovernmentDiscount gd : order.getGovDiscounts()) {
                gd.setOrderId(order.getOrderId()); // 关联主订单ID
                govMapper.insert(gd);
            }
        }

        // 4. 保存支付信息
        if (order.getPayments() != null) {
            for (OrderPayment pay : order.getPayments()) {
                pay.setOrderId(order.getOrderId()); // 关联主订单ID
                paymentMapper.insert(pay);
            }
        }

        // 5. 调用折扣引擎，对订单进行完整的金额、折扣和税费计算
        // 注意：这里传入了一个新的Store对象，实际应用中应从数据库或缓存中获取正确的店铺信息
        // 使用新的折扣计算引擎（NewDiscountEngine），支持更清晰的架构和业务规则验证
        NewDiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), new Store());

        // 6. 将计算结果回写到数据库（订单 + 明细 + 政府折扣）
        // 6.1 更新主订单金额、税务字段
        orderMapper.updateById(order);

        // 6.2 更新每一行商品的金额和税务字段
        if (order.getItems() != null) {
            for (SalesOrderItem item : order.getItems()) {
                if (item.getItemId() != null) {
                    itemMapper.updateById(item);
                }
            }
        }

        // 6.3 更新政府折扣的 lessVat / discount / addVat 字段
        if (order.getGovDiscounts() != null) {
            for (GovernmentDiscount gd : order.getGovDiscounts()) {
                if (gd.getGovDiscountId() != null) {
                    govMapper.updateById(gd);
                }
            }
        }

        // 7. 返回包含所有完整信息的订单对象
        return order;
    }

    /**
     * 根据订单ID获取订单的完整信息，包括其关联的商品项、折扣和支付信息。
     *
     * @param orderId 要查询的订单ID。
     * @return 包含所有关联信息的 {@link SalesOrder} 对象。
     */
    @Override
    public SalesOrder getOrderWithItems(Long orderId) {
        // 1. 查询主订单信息
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return null; // 如果订单不存在，返回null
        }
        // 2. 使用QueryWrapper根据order_id查询关联的商品项列表
        order.setItems(itemMapper.selectList(new QueryWrapper<SalesOrderItem>().eq("order_id", orderId)));
        // 3. 查询关联的政府折扣列表
        order.setGovDiscounts(govMapper.selectList(new QueryWrapper<GovernmentDiscount>().eq("order_id", orderId)));
        // 4. 查询关联的支付信息列表
        order.setPayments(paymentMapper.selectList(new QueryWrapper<OrderPayment>().eq("order_id", orderId)));

        return order;
    }

    /**
     * 订单实时预览（不做持久化）
     * <p>
     * 仅进行金额/税费/折扣计算，并生成一份发票文本返回给前端。
     */
    @Override
    public OrderPreviewResponse previewOrder(SalesOrder order) {
        // 1. 在内存中执行折扣计算（不插入数据库）
        Store store = new Store();
        store.setStoreId(1L);
        store.setName("Test Store");
        store.setAddress("Test Address");
        store.setVatRegTin("Test TIN");
        store.setBusinessType("RETAIL");

        Device device = new Device();
        device.setDeviceId(1L);
        device.setSn("Test SN");
        device.setMinNo("Test MIN");
        device.setTerminalNo("Test TERMINAL");
        device.setDateOfIssue(new Date());
        device.setPtuNo("Test PTU");

        User user = new User();
        user.setUserId(1L);
        user.setUsername("Test User");
        user.setPassword("Test Password");
        user.setPhone("Test Phone");
        user.setRole("Test Role");


        NewDiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        // 2. 生成小票文本
        com.zuno.bir.print.EnhancedReceiptPrintingService printingService =
                new com.zuno.bir.print.EnhancedReceiptPrintingService();
        String receipt = printingService.generateReceipt(order, store, device,user, false, null);

        // 3. 组装响应
        OrderPreviewResponse resp = new OrderPreviewResponse();
        resp.setOrder(order);
        resp.setReceiptContent(receipt);
        return resp;
    }
}
