package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 政府折扣实体类
 * <p>
 * 记录应用于订单的政府法定折扣的详细信息。
 * 它可以关联到整个订单，也可以关联到特定的商品项。
 * 使用 Lombok 的 @Data 注解和 Mybatis-Plus 的 @TableName 注解。
 */
@Data
@TableName("government_discount")
public class GovernmentDiscount {
    /**
     * 政府折扣记录ID，主键
     */   @TableId
    private Long govDiscountId;
    /**
     * 关联的销售订单ID (外键)
     */
    private Long orderId;
    /**
     * 关联的销售订单商品项ID (外键)。
     * 如果折扣是应用于整个订单或按人头计算（如团餐），此字段可以为null。
     * 如果折扣是针对特定商品，此字段将有值。
     */
    private Long itemId;
    /**
     * 享受折扣的人员类型。
     * 对应 {@link com.zuno.bir.enums.DiscountType} 枚举。
     * 例如: "SC", "PWD", "NAAC", "SP", "MOV", "DIPLOMATIC"。
     */
    private String personType;

    /**
     * (团餐模式专用) 记录享受此类型折扣的人数。
     * 例如，一个订单中有2位老年人，则 count 为 2。
     */
    private Integer count;

    /**
     * 因折扣而减去的增值税（VAT）金额。
     * 主要用于在小票上显示 "LESS 12% VAT" 的值。
     */
    private BigDecimal lessVat;
    /**
     * 实际的折扣金额。
     * 对于MGS等，这是基于净价计算的折扣；对于VBN，也是基于净价。
     */
    private BigDecimal discount;
    /**
     * 需要加回的增值税（VAT）金额。
     * 在某些计算模型中（例如整单含税模式），用于在小票上显示 "ADD 12% VAT"。
     */
    private BigDecimal addVat;
    /**
     * 额外信息。
     * 可以用于存储折扣相关的其他数据，如享受折扣人员的ID号、姓名等，通常为JSON格式字符串。
     */
    private String extraInfo;
}
