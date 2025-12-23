package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单商品项实体类
 * <p>
 * 代表销售订单中的一个具体商品行。
 * 包含了商品的详细信息、数量、价格以及经过折扣和税费计算后的详细财务数据。
 */
@Data
@TableName("sales_order_item")
public class SalesOrderItem {
    /**
     * 商品项ID，主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;
    /**
     * 关联的销售订单ID (外键)
     */
    private Long orderId;
    /**
     * 关联的商品ID (外键)
     */
    private Long productId;
    /**
     * 商品描述
     * 通常从商品主数据中复制而来，但可能在下单时被修改。
     */
    private String description;
    /**
     * 商品数量
     */
    private Integer quantity;
    /**
     * 商品的原始单价（含税）
     */
    private BigDecimal unitPrice;

    /**
     * 商品项的最终金额
     * 这是经过所有折扣和税费计算后，该商品项对订单总额的最终贡献值。
     * 对于应税商品，它等于 (净额 - 折扣 + VAT)。
     * 对于免税商品，它等于 (净额 - 折扣)。
     */
    private BigDecimal amount;

    /**
     * 商品的原始增值税（VAT）类型。
     * 从商品主数据复制而来，例如 "VATABLE", "VAT_EXEMPT"。
     */
    private String vatType;

    // === 核心折扣相关字段 ===

    /**
     * 应用于此商品项的折扣标签。
     * 例如 "MGS", "VBN", "SP" ,"NAAC","MOV" ,"DIPLOMATIC"。
     * 这个标签决定了该商品项如何参与折扣计算。
     */
    private String discountTag;

    /**
     * 应用于此商品项的常规折扣金额。
     * 这是商家提供的、非政府法定的折扣。
     */
    private BigDecimal regularDiscount;

    // --- 详细财务字段 (用于税务报告和审计) ---

    /**
     * 此商品项贡献的应税销售额 (Net of VAT)
     */
    private BigDecimal vatableSales;
    /**
     * 此商品项贡献的增值税金额
     */
    private BigDecimal vatAmount;
    /**
     * 此商品项贡献的免税销售额
     */
    private BigDecimal vatExemptSales;
    /**
     * 此商品项贡献的零税率销售额
     */
    private BigDecimal zeroRatedSales;

    // --- 非数据库映射字段 ---

    /**
     * 辅助字段，用于在内存中记录该行最终应用了哪种政府折扣类型（例如 "SC", "PWD"）。
     * 可用于生成更详细的报表。
     * 使用 @TableField(exist = false) 表示不映射到数据库表。
     */
    @TableField(exist = false)
    private String appliedDiscountType;
}
