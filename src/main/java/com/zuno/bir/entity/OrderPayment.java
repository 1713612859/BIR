package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单支付实体类
 * <p>
 * 记录了一笔订单的单次支付信息。一个订单可以有多种支付方式（例如，部分现金，部分信用卡）。
 * 使用 Lombok 的 @Data 注解和 Mybatis-Plus 的 @TableName 注解。
 */
@Data
@TableName("order_payment")
public class OrderPayment {
    /**
     * 支付记录ID，主键
     */   @TableId
    private Long paymentId;
    /**
     * 关联的销售订单ID (外键)
     */
    private Long orderId;
    /**
     * 支付方式类型。
     * 例如: "CASH" (现金), "CREDIT_CARD" (信用卡), "BALANCE" (余额支付)
     */
    private String type;
    /**
     * 本次支付的金额
     */
    private BigDecimal amount;
    /**
     * (信用卡支付专用) 信用卡号。
     * 在存储和显示时应进行脱敏处理。
     */
    private String cardNo;
    /**
     * (余额支付专用) 支付前的账户余额
     */
    private BigDecimal balanceBefore;
    /**
     * (余额支付专用) 支付后的账户余额
     */
    private BigDecimal balanceAfter;
    /**
     * (现金支付专用) 找零金额
     */
    private BigDecimal changeAmount;
}
