package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单实体类
 * <p>
 * 代表一笔完整的销售交易。这是系统的核心实体之一，汇总了订单的所有信息，
 * 包括基本信息、金额、税务明细以及关联的商品项、折扣和支付信息。
 */
@Data
@TableName("sales_order")
public class SalesOrder {
    /**
     * 订单ID，主键
     */
    @TableId
    private Long orderId;
    /**
     * 订单所属的店铺ID (外键)
     */
    private Long storeId;
    /**
     * 执行交易的设备ID (外键)
     */
    private Long deviceId;
    /**
     * 处理订单的收银员ID (外键)
     */
    private Long cashierId;

    /**
     * 业务类型，存储如 "RETAIL", "DINING", "FAST_FOOD" 等字符串。
     * 决定了使用哪种折扣计算策略。
     */
    private String businessType;
    /**
     * 销售发票编号 (Sales Invoice Number)
     */
    private String siNumber;
    /**
     * 交易类型，例如
     * "Dine-In(堂食)",
     * "Take-Out(打包 )",
     * "Delivery（外卖）"
     * ,"In-store（到店）"
     */
    private String trxType;
    /**
     * (餐饮专用) 桌台名称或编号
     */
    private String tableName;
    /**
     * (餐饮专用) 账单号
     */
    private Long billingNo;

    /**
     * (餐饮专用) 就餐人数。这是执行团餐折扣分摊的核心依据。
     */
    private Integer pax;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户手机号码
     */
    private String customerPhone;
    /**
     * 客户地址
     */
    private String customerAddress;

    /**
     * 客户 tin
     */
    private String customerTin;
    /**
     * 公司名称
     */
    private String CompanyName;

    // --- 金额相关字段 ---

    /**
     * 总销售额 (Gross Sales)
     * 所有商品项的原价总和，未扣除任何折扣。
     */
    private BigDecimal grossSales;
    /**
     * 常规折扣总额 (Regular Discount)
     * 商家提供的普通折扣（非政府法定折扣）的总和。
     */
    private BigDecimal regularDiscount;
    /**
     * 政府折扣总额 (Government Discount Total)
     * 包括法定折扣金额和因此减免的税额。
     */
    private BigDecimal govDiscountTotal;
    /**
     * 服务费
     */
    private BigDecimal serviceCharge;


    /**
     * 本地税  与 Service Charge 类似 ，通常 0
     */
    private BigDecimal LocalTax;

    /**
     * 最终应付金额 (Amount Due)
     * 客户需要支付的最终金额。
     */
    private BigDecimal amountDue;

    // --- 税务汇总数据 ---

    /**
     * 应税销售额 (Vatable Sales)
     * 订单中所有应税部分净额的总和。
     */
    private BigDecimal vatableSales;
    /**
     * 增值税总额 (VAT Amount)
     * 从应税销售额中计算出的增值税总和。
     */
    private BigDecimal vatAmount;
    /**
     * 免税销售额 (VAT Exempt Sales)
     * 订单中所有免税部分的金额总和。
     */
    private BigDecimal vatExemptSales;
    /**
     * 零税率销售额 (Zero-Rated Sales) 通常 0
     * 订单中所有零税率部分的金额总和。
     */
    private BigDecimal zeroRatedSales;

    // --- 非数据库映射字段 ---

    /**
     * 订单包含的商品项列表。
     * 使用 @TableField(exist = false) 表示该字段不与数据库表列对应，
     * 通常在查询时手动填充。
     */
    @TableField(exist = false)
    private List<SalesOrderItem> items;

    /**
     * 应用于此订单的政府折扣信息列表。
     */
    @TableField(exist = false)
    private List<GovernmentDiscount> govDiscounts;

    /**
     * 订单的支付信息列表。
     */
    @TableField(exist = false)
    private List<OrderPayment> payments;

    /**
     * 标记是否使用团餐折扣逻辑。
     * 这个值通常由前端根据用户操作（例如，在餐饮场景下选择按人头分摊）传入，
     * 用于指导后端折扣引擎选择正确的计算策略。
     */
    @TableField(exist = false)
    private boolean isGroupMeal;
    
    /**
     * 订单创建时间（订单完成时间）
     */
    private Date orderDate;
    
    /**
     * 订单备注
     */
    private String remarks;
}
