package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 店铺实体类
 * <p>
 * 代表一个独立的店铺或分支机构，包含了店铺的基本信息和特定配置（如服务费）。
 * 使用 Lombok 的 @Data 注解自动生成标准方法。
 * 使用 Mybatis-Plus 的 @TableName 注解映射到数据库的 "store" 表。
 */
@Data
@TableName("store")
public class Store {
    /**
     * 店铺ID，主键
     */
    @TableId
    private Long storeId;
    /**
     * 店铺名称（例如，“Zuno Coffee - BGC Branch”）
     */
    private String name;
    /**
     * 公司法定名称
     */
    private String companyName;

    /**
     * 公司法定地址
     */
    private String companyAddress;
    /**
     * 店铺的详细地址
     */
    private String address;
    /**
     * 增值税注册税务识别号 (VAT-Registered Taxpayer Identification Number)
     */
    private String vatRegTin;


    /**
     * 店铺的经纬度坐标
     */
    private String latLng;
    /**
     * 店铺的营业时间
     * 格式: "09:00-17:00"
     */
    private String operatingHours;
    /**
     * 营业日
     */
    private String operatingDays;
    /**
     * 店铺营业类型
     * 示例值: "RETAIL"
     * 对应 {@link com.zuno.bir.enums.BusinessType} 枚举。
     */
    private String businessType;


    /**
     * 店铺联系电话
     */
    private String phone;
    /**
     * 店铺联系邮箱
     */
    private String email;

    // === 服务费配置 ===

    /**
     * 服务费类型。
     * 可能的值:
     * - "PERCENTAGE": 按百分比收费。
     * - "FIXED_AMOUNT": 按固定金额收费。
     */
    private String serviceChargeType;

    /**
     * 服务费的具体数值。
     * - 如果 serviceChargeType 是 "PERCENTAGE"，这里存储的是小数形式的百分比（例如，10% 存储为 0.10）。
     * - 如果 serviceChargeType 是 "FIXED_AMOUNT"，这里存储的是固定的金额（例如，50.00）。
     */
    private BigDecimal serviceChargeValue;

    /**
     * 商家自定义信息1
     */
    private String customMessage1;

    /**
     * 商家自定义信息2（软件供应商自定义信息）
     */
    private String customMessage2;
}
