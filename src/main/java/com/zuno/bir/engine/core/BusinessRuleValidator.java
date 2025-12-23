package com.zuno.bir.engine.core;

import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.DiscountType;

import java.util.List;

/**
 * 【核心组件】业务规则验证器
 * <p>
 * <b>职责：</b>
 * 在订单计算之前，验证订单数据是否符合各业态的业务规则约束。
 * 如果违反规则，抛出 {@link IllegalArgumentException} 异常，阻止计算继续进行。
 * <p>
 * <b>验证时机：</b>
 * 在 {@link NewDiscountEngine#calculateOrder} 中，创建 {@link CalculationContext} 之后、
 * 调用 {@link OrderCalculator#calculate} 之前执行验证。
 * <p>
 * <b>各业态验证规则：</b>
 * <ul>
 *   <li><b>RETAIL（零售）</b>：
 *       <ul>
 *         <li>政府折扣人群只能有一个（不能多个不同的折扣类型）</li>
 *         <li>如果有政府折扣，不能同时使用整单折扣（金额或百分比）</li>
 *         <li>如果没有政府折扣，可以使用整单折扣</li>
 *       </ul>
 *   </li>
 *   <li><b>DINING（餐饮）</b>：
 *       <ul>
 *         <li>单品折扣模式：只能有一个政府折扣人群</li>
 *         <li>整单折扣模式（Group Meal）：可以支持多个政府折扣人群，但：
 *             <ul>
 *               <li>不支持 DIPLOMATIC 折扣</li>
 *               <li>不支持 SP 折扣</li>
 *             </ul>
 *         </li>
 *       </ul>
 *   </li>
 *   <li><b>FAST_FOOD（快餐）</b>：
 *       <ul>
 *         <li>只支持单品折扣模式</li>
 *         <li>只能有一个政府折扣人群</li>
 *         <li>不支持整单折扣（按人数分摊）</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 * CalculationContext context = CalculationContext.builder()
 *     .order(order)
 *     .items(items)
 *     .governmentDiscounts(discounts)
 *     .businessType(BusinessType.RETAIL)
 *     .build();
 * 
 * // 验证业务规则（如果违反规则会抛出异常）
 * BusinessRuleValidator.validate(context);
 * }</pre>
 */
public class BusinessRuleValidator {

    /**
     * 验证订单是否符合业务规则
     *
     * @param context 计算上下文
     * @throws IllegalArgumentException 如果违反业务规则
     */
    public static void validate(CalculationContext context) {
        BusinessType bizType = context.getBusinessType();
        List<GovernmentDiscount> discounts = context.getGovernmentDiscounts();

        if(bizType == BusinessType.RETAIL) {
            validateRetailRules(context, discounts);
        } else if(bizType == BusinessType.DINING) {
            validateDiningRules(context, discounts);
        } else if(bizType == BusinessType.FAST_FOOD) {
            validateFastFoodRules(context, discounts);
        }
    }

    /**
     * 验证零售业态规则
     * <p>
     * 规则：
     * - 政府折扣人群只能有一个（不能多个）
     * - 如果没有政府折扣，可以设置整单金额折扣或百分比折扣
     */
    private static void validateRetailRules(CalculationContext context, List<GovernmentDiscount> discounts) {
        if(discounts != null && !discounts.isEmpty()) {
            // 零售业态：政府折扣人群只能有一个
            // 需要检查是否有多个不同的personType
            long distinctPersonTypes = discounts.stream()
                    .map(GovernmentDiscount::getPersonType)
                    .distinct()
                    .count();

            if(distinctPersonTypes > 1) {
                throw new IllegalArgumentException(
                        "零售业态只支持一个政府折扣人群，当前有 " + distinctPersonTypes + " 个不同的折扣类型");
            }

            // 如果有政府折扣，不能同时有整单折扣
            if(context.getWholeOrderDiscountAmount() != null &&
                    context.getWholeOrderDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("零售业态：有政府折扣时，不能同时使用整单金额折扣");
            }

            if(context.getWholeOrderDiscountPercent() != null &&
                    context.getWholeOrderDiscountPercent().compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("零售业态：有政府折扣时，不能同时使用整单百分比折扣");
            }
        }
    }

    /**
     * 验证餐饮业态规则
     * <p>
     * 规则：
     * - 单品折扣模式：只能有一个政府折扣人群
     * - 整单折扣模式：可以支持多个政府折扣人群（按人数分摊）
     */
    private static void validateDiningRules(CalculationContext context, List<GovernmentDiscount> discounts) {
        SalesOrder order = context.getOrder();

        if(order.isGroupMeal()) {
            // 整单折扣模式：可以支持多个政府折扣人群
            // 但需要检查是否有DIPLOMATIC折扣（不支持整单折扣）
            if(discounts != null) {
                boolean hasDiplomatic = discounts.stream()
                        .anyMatch(gd->DiscountType.DIPLOMATIC.name().equals(gd.getPersonType()));

                if(hasDiplomatic) {
                    throw new IllegalArgumentException(
                            "餐饮业态：DIPLOMATIC折扣不支持整单折扣模式，请使用单品折扣模式");
                }

                // 检查是否有SP折扣（不支持整单折扣）
                boolean hasSP = discounts.stream()
                        .anyMatch(gd->DiscountType.SP.name().equals(gd.getPersonType()));

                if(hasSP) {
                    throw new IllegalArgumentException(
                            "餐饮业态：SP折扣不支持整单折扣模式，请使用单品折扣模式");
                }
            }
        } else {
            // 单品折扣模式：只能有一个政府折扣人群
            if(discounts != null && discounts.size() > 1) {
                // 检查是否有多个不同的personType（排除itemId不同的情况）
                long distinctPersonTypes = discounts.stream()
                        .map(GovernmentDiscount::getPersonType)
                        .distinct()
                        .count();

                if(distinctPersonTypes > 1) {
                    throw new IllegalArgumentException(
                            "餐饮业态单品折扣模式只支持一个政府折扣人群，当前有 " + distinctPersonTypes + " 个不同的折扣类型");
                }
            }
        }
    }

    /**
     * 验证快餐业态规则
     * <p>
     * 规则：和餐饮业态一致
     */
    private static void validateFastFoodRules(CalculationContext context, List<GovernmentDiscount> discounts) {
        // 快餐业态不支持整单折扣（按人数分摊）
        // 只能使用单品折扣模式，且只能有一个政府折扣人群
        if(discounts != null && discounts.size() > 1) {
            long distinctPersonTypes = discounts.stream()
                    .map(GovernmentDiscount::getPersonType)
                    .distinct()
                    .count();

            if(distinctPersonTypes > 1) {
                throw new IllegalArgumentException(
                        "快餐业态只支持一个政府折扣人群，当前有 " + distinctPersonTypes + " 个不同的折扣类型");
            }
        }
    }
}

