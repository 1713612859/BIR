package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 设备实体类
 * <p>
 * 代表用于进行销售的物理设备，如POS机或收银终端。
 * 包含了税务局要求的设备标识信息。
 * 使用 Lombok 的 @Data 注解和 Mybatis-Plus 的 @TableName 注解。
 */
@Data
@TableName("device")
public class Device {
    /**
     * 设备ID，主键
     */   @TableId
    private Long deviceId;
    /**
     * 设备所属的店铺ID (外键，关联 store.storeId)
     */
    private Long storeId;
    /**
     * 设备序列号 (Serial Number)
     * 这是设备制造商提供的唯一标识。
     */
    private String sn;
    /**
     * 机器识别号 (Machine Identification Number)
     * 这是税务认证时分配给设备的唯一编号。
     */
    private String minNo;
    /**
     * 终端号
     * 在店铺内部用于区分不同收银点的编号。
     */
    private String terminalNo;
    
    /**
     * 认证日期（Date of Issue）
     */
    private java.util.Date dateOfIssue;
    
    /**
     * PTU编号（Permit to Use Number）
     */
    private String ptuNo;
}
