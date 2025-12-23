package com.zuno.bir.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品实体类
 * <p>
 * 代表一个可供销售的商品或服务。
 * 包含了商品的基本信息、价格、税务属性和折扣资格标签。
 * 使用 Lombok 的 @Data 注解和 Mybatis-Plus 的 @TableName 注解。
 */
@Data
@TableName("product")
public class Product {
    /**
     * 商品ID，主键
     */   @TableId
    private Long productId;
    /**
     * 商品名称
     */
    private String name;
    /**
     * 商品的单价
     */
    private BigDecimal price;
    /**
     * 商品的增值税（VAT）类型。
     * 可能的值:
     * - "VATABLE": 应税商品
     * - "VAT_EXEMPT": 免税商品
     */
    private String vatType;
    /**
     * 商品适用的业务类型。
     * 例如：RETAIL (零售), DINING (餐饮), FAST_FOOD (快餐)
     * 这个字段可以用来限制商品只能在特定类型的业务中销售。
     */
    private String businessType;
    /**
     * 商品的折扣标签列表。
     * 这个列表定义了该商品有资格参与哪些类型的折扣计算。
     * 示例值: ["MGS", "VBN"]
     * 对应 {@link com.zuno.bir.enums.ItemTag} 枚举。
     */
    private List<String> discountTags;
}
