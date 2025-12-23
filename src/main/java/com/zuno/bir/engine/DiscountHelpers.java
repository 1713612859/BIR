package com.zuno.bir.engine;

import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.DiscountType;
import com.zuno.bir.enums.ItemTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 折扣计算帮助类
 * 提供处理销售订单中各种折扣和税费计算的静态辅助方法。
 */
public class DiscountHelpers {

    // 定义税率常量
    public static final BigDecimal VAT_RATE = new BigDecimal("0.12"); // 增值税（VAT）税率，12%
    public static final BigDecimal NON_VAT_RATE = new BigDecimal("0.00"); // 非增值税税率，0%
    public static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12"); // 用于从含税价格中计算净价的增值税除数 (1 + VAT_RATE)
    public static final BigDecimal NON_VAT_RATE_DIVISOR = new BigDecimal("1.00"); // 用于非增值税情况的除数

    /**
     * 内部类，用于封装增值税计算所需的除数和税率。
     */
    public static class VatCalculationDetails {
        public final BigDecimal divisor; // 除数
        public final BigDecimal rate;    // 税率

        public VatCalculationDetails(BigDecimal divisor, BigDecimal rate) {
            this.divisor = divisor;
            this.rate = rate;
        }
    }

    /**
     * 根据给定的VAT类型字符串，返回相应的VatCalculationDetails实例。
     *
     * @param vatType VAT类型 ("VAT_EXEMPT", "ZERO_RATED" 或其他)
     * @return 包含正确除数和税率的VatCalculationDetails对象
     */
    public static VatCalculationDetails getVatDetails(String vatType) {
        if("VAT_EXEMPT".equalsIgnoreCase(vatType) || "ZERO_RATED".equalsIgnoreCase(vatType)) {
            return new VatCalculationDetails(NON_VAT_RATE_DIVISOR, NON_VAT_RATE);
        }
        return new VatCalculationDetails(VAT_DIVISOR, VAT_RATE);
    }

    /**
     * 根据总价和VAT类型计算净价（不含税价格）。
     *
     * @param grossPrice 总价（含税）
     * @param vatType    VAT类型
     * @param scale      结果保留的小数位数
     * @return 计算出的净价
     */
    public static BigDecimal calculateNetPrice(BigDecimal grossPrice, String vatType, int scale) {
        return grossPrice.divide(getVatDetails(vatType).divisor, scale, RoundingMode.HALF_UP);
    }

    /**
     * 根据总价和VAT类型计算VAT金额。
     *
     * @param grossPrice 总价（含税）
     * @param vatType    VAT类型
     * @param scale      结果保留的小数位数
     * @return 计算出的VAT金额
     */
    public static BigDecimal calculateVatAmount(BigDecimal grossPrice, String vatType, int scale) {
        if(getVatDetails(vatType).rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return grossPrice.subtract(calculateNetPrice(grossPrice, vatType, scale));
    }

    /**
     * 计算订单的最终应付金额。
     *
     * @param order 销售订单对象
     * @param items 订单中的商品项列表
     */
    public static void calculateFinalAmountDue(SalesOrder order, List<SalesOrderItem> items) {
        // 业务规则：
        // 零售业态：
        //   Amount Due = Gross Sales - Regular Discount - Government Discount + Service Charge + Local Tax
        //   （由订单级汇总字段直接计算，保证与票面一致）
        // 其他业态：
        //   仍按逐行金额汇总 + Service Charge + Local Tax 计算

        BusinessType biz = parseBusinessType(order.getBusinessType());

        BigDecimal s = order.getServiceCharge() != null ? order.getServiceCharge() : BigDecimal.ZERO;
        BigDecimal t = order.getLocalTax() != null ? order.getLocalTax() : BigDecimal.ZERO;
        order.setLocalTax(t);

        if (biz == BusinessType.RETAIL) {
            BigDecimal gross = order.getGrossSales() != null ? order.getGrossSales() : BigDecimal.ZERO;
            BigDecimal reg   = order.getRegularDiscount() != null ? order.getRegularDiscount() : BigDecimal.ZERO;
            BigDecimal gov   = order.getGovDiscountTotal() != null ? order.getGovDiscountTotal() : BigDecimal.ZERO;

            BigDecimal due = gross
                    .subtract(reg)
                    .subtract(gov)
                    .add(s)
                    .add(t)
                    .setScale(2, RoundingMode.HALF_UP);
            order.setAmountDue(due);
            return;
        }

        // 非零售：按行金额累加再加上服务费和本地税
        BigDecimal totalItemAmount = BigDecimal.ZERO;
        if(items != null) {
            for(SalesOrderItem item : items) {
                if(item.getAmount() != null) {
                    totalItemAmount = totalItemAmount.add(item.getAmount());
                }
            }
        }

        order.setAmountDue(totalItemAmount.add(s).add(t).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 从政府折扣列表中为特定商品项查找适用的折扣。
     * 查找逻辑：优先精确匹配商品ID，其次匹配全局折扣。
     *
     * @param list   政府折扣列表
     * @param itemId 商品项ID
     * @return 找到的GovernmentDiscount对象，如果没找到则返回null
     */
    public static GovernmentDiscount findDiscountForItem(List<GovernmentDiscount> list, Long itemId) {
        if(list == null) return null;

        // 1. 精确匹配：查找专门针对该商品ID的折扣
        GovernmentDiscount specific = list.stream().filter(d->Objects.equals(d.getItemId(), itemId) && d.getItemId() != null).findFirst().orElse(null);
        if(specific != null) return specific;

        // 2. 全局匹配：如果找不到精确匹配，则查找适用于所有商品的全局折扣 (itemId为null)
        GovernmentDiscount global = list.stream().filter(d->d.getItemId() == null).findFirst().orElse(null);

        if(global != null) {
            DiscountType type = parseDiscountType(global.getPersonType());
            // 确保全局折扣是有效的折扣类型
            if(type == DiscountType.SC || type == DiscountType.PWD || type == DiscountType.NAAC || type == DiscountType.MOV || type == DiscountType.SP || type == DiscountType.DIPLOMATIC) {
                return global;
            }
        }
        return null;
    }

    /**
     * 将字符串安全地解析为BusinessType枚举。
     *
     * @param s 业务类型字符串
     * @return BusinessType枚举，解析失败则返回默认值RETAIL
     */
    public static BusinessType parseBusinessType(String s) {
        try {
            return BusinessType.valueOf(s);
        } catch(Exception e) {
            return BusinessType.RETAIL;
        }
    }

    /**
     * 将字符串安全地解析为DiscountType枚举。
     *
     * @param s 折扣类型字符串
     * @return DiscountType枚举，解析失败则返回默认值SC
     */
    public static DiscountType parseDiscountType(String s) {
        try {
            return DiscountType.valueOf(s);
        } catch(Exception e) {
            return DiscountType.SC;
        }
    }

    /**
     * 将字符串安全地解析为ItemTag枚举。
     *
     * @param s 商品标签字符串
     * @return ItemTag枚举，解析失败则返回默认值MGS
     */
    public static ItemTag parseItemTag(String s) {
        try {
            return ItemTag.valueOf(s);
        } catch(Exception e) {
            return ItemTag.MGS;
        }
    }

    /**
     * 判断在给定的业务类型、折扣类型和商品标签下，是否允许应用折扣。
     * <p>
     * 业务规则：
     * - 零售：支持SC、PWD、NAAC、SP、MOV、DIPLOMATIC
     * - 餐饮：支持SC、PWD、NAAC、MOV、DIPLOMATIC（不支持SP）
     * - 快餐&奶茶：支持SC、PWD、NAAC、MOV、DIPLOMATIC（不支持SP）
     * <p>
     * 商品标签映射：
     * - MGS：零售/餐饮/快餐支持SC/PWD/NAAC/MOV
     * - VBN：仅零售支持SC/PWD
     * - SP：仅零售支持SP
     * - NAAC：仅零售支持NAAC
     * - MOV：仅零售支持MOV
     * - 含税商品：所有业态支持DIPLOMATIC
     *
     * @param biz  业务类型
     * @param type 折扣类型
     * @param tag  商品标签
     * @return 如果允许折扣则返回true，否则返回false
     */
    public static boolean isDiscountAllowed(BusinessType biz, DiscountType type, ItemTag tag) {
        // 【关键修正 1】: 外交官折扣(DIPLOMATIC)拥有最高优先级，允许对任何含税商品生效，无视商品标签和业务类型限制。
        if(type == DiscountType.DIPLOMATIC) {
            return true;
        }

        // 餐饮和快餐业务不适用单亲父母(SP)折扣
        if((biz == BusinessType.DINING || biz == BusinessType.FAST_FOOD) && type == DiscountType.SP) {
            return false;
        }

        switch (tag) {
            case MGS:
                // MGS商品：零售/餐饮/快餐都支持SC/PWD/NAAC/MOV
                return type == DiscountType.SC || type == DiscountType.PWD || 
                       type == DiscountType.NAAC || type == DiscountType.MOV;
            case VBN:
                // VBN商品：仅零售支持SC/PWD折扣，餐饮和快餐不支持
                if(biz == BusinessType.DINING || biz == BusinessType.FAST_FOOD) {
                    return false;
                }
                return type == DiscountType.SC || type == DiscountType.PWD;
            case SP:
                // SP商品：仅零售支持SP折扣
                if(biz == BusinessType.DINING || biz == BusinessType.FAST_FOOD) {
                    return false;
                }
                return type == DiscountType.SP;
            case NAAC:
                // NAAC商品：仅零售支持NAAC折扣
                if(biz == BusinessType.DINING || biz == BusinessType.FAST_FOOD) {
                    return false;
                }
                return type == DiscountType.NAAC;
            case MOV:
                // MOV商品：仅零售支持MOV折扣
                if(biz == BusinessType.DINING || biz == BusinessType.FAST_FOOD) {
                    return false;
                }
                return type == DiscountType.MOV;
            case DIPLOMATIC:
                // DIPLOMATIC商品：所有业态支持DIPLOMATIC折扣
                return type == DiscountType.DIPLOMATIC;
            default:
                // 对于没有标签(NONE)的普通商品，只有外交官折扣能生效（已在上方处理）。
                return false;
        }
    }
    
    /**
     * 判断折扣类型是否支持整单折扣（按人数比例分摊）。
     * <p>
     * 业务规则：
     * - 只有餐饮业务支持整单折扣
     * - 整单折扣支持：SC、PWD、NAAC、MOV
     * - 整单折扣不支持：SP、DIPLOMATIC
     * - DIPLOMATIC和其他折扣互斥，不支持整单折扣
     *
     * @param discountType 折扣类型
     * @return 如果支持整单折扣则返回true，否则返回false
     */
    public static boolean isGroupMealDiscountAllowed(DiscountType discountType) {
        if (discountType == null) {
            return false;
        }
        // 整单折扣支持：SC、PWD、NAAC、MOV
        // 不支持：SP、DIPLOMATIC
        return discountType == DiscountType.SC || 
               discountType == DiscountType.PWD || 
               discountType == DiscountType.NAAC || 
               discountType == DiscountType.MOV;
    }
    
    /**
     * 检查订单是否包含DIPLOMATIC折扣。
     * <p>
     * DIPLOMATIC折扣和其他折扣互斥，如果存在DIPLOMATIC折扣，则不应使用整单折扣逻辑。
     *
     * @param discounts 政府折扣列表
     * @return 如果包含DIPLOMATIC折扣则返回true，否则返回false
     */
    public static boolean containsDiplomaticDiscount(List<GovernmentDiscount> discounts) {
        if (discounts == null || discounts.isEmpty()) {
            return false;
        }
        return discounts.stream()
                .anyMatch(gd -> DiscountType.DIPLOMATIC == parseDiscountType(gd.getPersonType()));
    }

    /**
     * 根据折扣类型和商品标签获取折扣率。
     * <p>
     * 折扣率规则：
     * - MGS商品：20%（适用于SC、PWD、NAAC、MOV）
     * - VBN商品：5%（仅零售，适用于SC、PWD）
     * - SP商品：10%（仅零售，适用于SP）
     * - NAAC商品：20%（仅零售，适用于NAAC）
     * - MOV商品：20%（仅零售，适用于MOV）
     * - DIPLOMATIC：0%（免税但不打折）
     * <p>
     * 注意：餐饮和快餐的NAAC折扣类型应用于MGS商品时，也是20%折扣率。
     *
     * @param type 折扣类型
     * @param tag  商品标签
     * @return 折扣率的BigDecimal值
     */
    public static BigDecimal getDiscountRate(DiscountType type, ItemTag tag) {
        // 【关键修正 2】: 外交官折扣永远是0%折扣率，他们只享受免税待遇。
        if(type == DiscountType.DIPLOMATIC) return BigDecimal.ZERO;

        if(tag == ItemTag.MGS) return new BigDecimal("0.20"); // 20%（适用于SC、PWD、NAAC、MOV）
        if(tag == ItemTag.VBN) return new BigDecimal("0.05"); // 5%（仅零售，适用于SC、PWD）
        if(tag == ItemTag.SP) return new BigDecimal("0.10"); // 10%（仅零售，适用于SP）
        if(tag == ItemTag.NAAC || tag == ItemTag.MOV) return new BigDecimal("0.20"); // 20%（仅零售）
        return BigDecimal.ZERO;
    }

    /**
     * 重置销售订单中的所有累计总额为零。
     *
     * @param order 要重置的销售订单
     */
    public static void resetTotals(SalesOrder order) {
        order.setGrossSales(BigDecimal.ZERO);
        order.setRegularDiscount(BigDecimal.ZERO);
        order.setGovDiscountTotal(BigDecimal.ZERO);
        order.setVatableSales(BigDecimal.ZERO);
        order.setVatExemptSales(BigDecimal.ZERO);
        order.setZeroRatedSales(BigDecimal.ZERO);
        order.setVatAmount(BigDecimal.ZERO);
        order.setServiceCharge(BigDecimal.ZERO);
    }

    /**
     * 重置政府折扣列表中的折扣金额为零。
     *
     * @param discounts 政府折扣列表
     */
    public static void resetDiscountAmounts(List<GovernmentDiscount> discounts) {
        if(discounts != null && discounts.size() > 0) {
            for(GovernmentDiscount discount : discounts) {
                discount.setDiscount(BigDecimal.ZERO);
                discount.setAddVat(BigDecimal.ZERO);
                discount.setLessVat(BigDecimal.ZERO);
            }
        }
    }

    /**
     * 将计算出的各项总额填充到销售订单对象中。
     */
    public static void fillOrderTotals(SalesOrder order, BigDecimal totalGross, BigDecimal totalRegDisc, BigDecimal totalGovDisc, BigDecimal totalVatable, BigDecimal totalVatExempt, BigDecimal totalZeroRated, BigDecimal totalVatAmount) {
        // 填充 各项总额
        // 订单总额 ，商品行 累计
        order.setGrossSales(totalGross);
        // 常规折扣
        order.setRegularDiscount(totalRegDisc);
        // 政府折扣
        order.setGovDiscountTotal(totalGovDisc);
        // Vatable sales ，商品行 累计
        order.setVatableSales(totalVatable);
        // Vat-exempt sales ，商品行 累计
        order.setVatExemptSales(totalVatExempt);
        // Zero-rated sales ，商品行 累计
        order.setZeroRatedSales(totalZeroRated);
        // Vat amount （税），商品行 累计
        order.setVatAmount(totalVatAmount);
    }

    /**
     * 根据服务费计算基础和店铺设置，计算服务费。
     *
     * @param order        销售订单
     * @param store        店铺信息
     * @param totalSCBasis 用于计算服务费的基础金额
     */
    public static void calculateServiceCharge(SalesOrder order, Store store, BigDecimal totalSCBasis) {
        if(store == null) {
            order.setServiceCharge(BigDecimal.ZERO);
            return;
        }
        if(totalSCBasis.compareTo(BigDecimal.ZERO) <= 0) {
            order.setServiceCharge(BigDecimal.ZERO);
            return;
        }
        BigDecimal scValue = store.getServiceChargeValue() != null ? store.getServiceChargeValue() : BigDecimal.ZERO;
        BigDecimal finalSC = "FIXED_AMOUNT".equalsIgnoreCase(store.getServiceChargeType()) ? scValue : totalSCBasis.multiply(scValue).setScale(2, RoundingMode.HALF_UP);
        order.setServiceCharge(finalSC);
    }

    /**
     * 检查订单是否为外交官订单。
     *
     * @param discounts 政府折扣列表
     * @return 如果列表中包含外交官折扣，则返回true
     */
    public static boolean isDiplomaticOrder(List<GovernmentDiscount> discounts) {
        return discounts != null && discounts.stream().anyMatch(d->"DIPLOMATIC".equalsIgnoreCase(d.getPersonType()));
    }

    /**
     * (已废弃) 旧的外交官订单处理逻辑。
     * 当前版本中，外交官折扣已统一整合到Item-Level计算流程中，此方法不再需要。
     *
     * @return 返回零。
     */
    @Deprecated
    public static BigDecimal handleDiplomatic(SalesOrder order, List<SalesOrderItem> items, List<GovernmentDiscount> discounts) {
        // 此方法逻辑已整合入 ItemLevelCalculator，不再单独使用。
        return BigDecimal.ZERO;
    }
}
