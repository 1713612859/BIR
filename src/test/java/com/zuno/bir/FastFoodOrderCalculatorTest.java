package com.zuno.bir;

import com.zuno.bir.engine.core.CalculationContext;
import com.zuno.bir.engine.core.CalculationResult;
import com.zuno.bir.engine.core.impl.FastFoodOrderCalculator;
import com.zuno.bir.entity.*;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 快餐业态订单计算器测试
 */
public class FastFoodOrderCalculatorTest {
    
    private FastFoodOrderCalculator calculator = new FastFoodOrderCalculator();
    
    @Test
    @DisplayName("快餐业态 - 无折扣订单")
    public void testFastFoodWithoutDiscount() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 1);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(null)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertEquals(new BigDecimal("100.00"), result.getTotalVatableSales());
        assertEquals(new BigDecimal("12.00"), result.getTotalVatAmount());
        assertEquals(BigDecimal.ZERO, result.getTotalGovernmentDiscount());
    }
    
    @Test
    @DisplayName("快餐业态 - SC折扣（MGS商品）")
    public void testFastFoodWithSCDiscount() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果：20%折扣，免税
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(BigDecimal.ZERO, result.getTotalVatAmount()); // 免税
    }
    
    @Test
    @DisplayName("快餐业态 - PWD折扣")
    public void testFastFoodWithPWDDiscount() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.PWD, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
    }
    
    @Test
    @DisplayName("快餐业态 - 不支持SP折扣")
    public void testFastFoodWithSPDiscount() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "SP", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SP, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果：SP折扣不应该生效
        assertEquals(BigDecimal.ZERO, result.getTotalGovernmentDiscount());
    }
    
    @Test
    @DisplayName("快餐业态 - 不支持VBN商品折扣")
    public void testFastFoodWithVBNItem() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "VBN", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果：VBN商品折扣不应该生效
        assertEquals(BigDecimal.ZERO, result.getTotalGovernmentDiscount());
    }
    
    @Test
    @DisplayName("快餐业态 - DIPLOMATIC折扣（免税但不打折）")
    public void testFastFoodWithDiplomaticDiscount() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.DIPLOMATIC, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果：DIPLOMATIC免税但不打折
        assertEquals(BigDecimal.ZERO, result.getTotalGovernmentDiscount()); // 折扣为0
        assertEquals(BigDecimal.ZERO, result.getTotalVatAmount()); // 免税
    }
    
    @Test
    @DisplayName("快餐业态 - 多个商品")
    public void testFastFoodWithMultipleItems() {
        SalesOrder order = createFastFoodOrder();
        List<SalesOrderItem> items = new ArrayList<>();
        
        items.add(createItem("Burger", "VATABLE", "MGS", new BigDecimal("112.00"), 2));
        items.add(createItem("Fries", "VATABLE", "NONE", new BigDecimal("56.00"), 1));
        
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.FAST_FOOD)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果
        assertEquals(new BigDecimal("280.00"), result.getTotalGrossSales());
    }
    
    // ==================== 辅助方法 ====================
    
    private SalesOrder createFastFoodOrder() {
        SalesOrder order = new SalesOrder();
        order.setBusinessType(BusinessType.FAST_FOOD.name());
        order.setSiNumber("000000001");
        order.setTrxType("Take-Out(打包)");
        return order;
    }
    
    private List<SalesOrderItem> createItems(String vatType, String discountTag, BigDecimal unitPrice, int quantity) {
        List<SalesOrderItem> items = new ArrayList<>();
        items.add(createItem("Test Item", vatType, discountTag, unitPrice, quantity));
        return items;
    }
    
    private SalesOrderItem createItem(String description, String vatType, String discountTag, 
                                     BigDecimal unitPrice, int quantity) {
        SalesOrderItem item = new SalesOrderItem();
        item.setDescription(description);
        item.setVatType(vatType);
        item.setDiscountTag(discountTag);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        return item;
    }
    
    private List<GovernmentDiscount> createGovernmentDiscounts(DiscountType discountType, Long itemId) {
        List<GovernmentDiscount> discounts = new ArrayList<>();
        GovernmentDiscount gd = new GovernmentDiscount();
        gd.setPersonType(discountType.name());
        gd.setItemId(itemId);
        discounts.add(gd);
        return discounts;
    }
}

