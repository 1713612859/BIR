package com.zuno.bir.engine.core;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计算结果
 * <p>
 * 封装计算引擎返回的结果数据。
 */
@Data
@Builder
public class CalculationResult {
    /**
     * 总销售额（Gross Sales）
     */
    private BigDecimal totalGrossSales;
    
    /**
     * 总常规折扣（Regular Discount）
     */
    private BigDecimal totalRegularDiscount;
    
    /**
     * 总政府折扣（Government Discount）
     */
    private BigDecimal totalGovernmentDiscount;
    
    /**
     * 应税销售额（Vatable Sales）
     */
    private BigDecimal totalVatableSales;
    
    /**
     * 免税销售额（VAT Exempt Sales）
     */
    private BigDecimal totalVatExemptSales;
    
    /**
     * 零税率销售额（Zero-Rated Sales）
     */
    private BigDecimal totalZeroRatedSales;
    
    /**
     * 增值税总额（VAT Amount）
     */
    private BigDecimal totalVatAmount;
    
    /**
     * 服务费计算基础（Service Charge Basis）
     * 用于计算服务费的基础金额
     */
    private BigDecimal serviceChargeBasis;
}

