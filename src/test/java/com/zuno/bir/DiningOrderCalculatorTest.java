package com.zuno.bir;

import com.zuno.bir.engine.core.CalculationContext;
import com.zuno.bir.engine.core.CalculationResult;
import com.zuno.bir.engine.core.impl.DiningOrderCalculator;
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
 * 餐饮业态订单计算器测试
 */
public class DiningOrderCalculatorTest {
    
    private DiningOrderCalculator calculator = new DiningOrderCalculator();
    
    @Test
    @DisplayName("餐饮业态 - 单品折扣模式（单个政府折扣）")
    public void testDiningItemLevelDiscount() {
        SalesOrder order = createDiningOrder(false); // 非团餐
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.DINING)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
    }
    
    @Test
    @DisplayName("餐饮业态 - 整单折扣模式（多个政府折扣人群）")
    public void testDiningGroupMealMultipleDiscounts() {
        SalesOrder order = createDiningOrder(true); // 团餐
        order.setPax(5); // 5个人
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 5);
        
        // 创建多个折扣人群：2个MOV，1个PWD，1个NAAC，1个无折扣
        List<GovernmentDiscount> discounts = new ArrayList<>();
        
        GovernmentDiscount mov1 = new GovernmentDiscount();
        mov1.setPersonType(DiscountType.MOV.name());
        mov1.setCount(2);
        discounts.add(mov1);
        
        GovernmentDiscount pwd = new GovernmentDiscount();
        pwd.setPersonType(DiscountType.PWD.name());
        pwd.setCount(1);
        discounts.add(pwd);
        
        GovernmentDiscount naac = new GovernmentDiscount();
        naac.setPersonType(DiscountType.NAAC.name());
        naac.setCount(1);
        discounts.add(naac);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.DINING)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果：按人数分摊折扣
        assertEquals(new BigDecimal("560.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        
        // 验证每个折扣人群都有折扣金额
        assertTrue(mov1.getDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(pwd.getDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(naac.getDiscount().compareTo(BigDecimal.ZERO) > 0);
    }
    
    @Test
    @DisplayName("餐饮业态 - 整单折扣模式（DIPLOMATIC不支持）")
    public void testDiningGroupMealWithDiplomatic() {
        SalesOrder order = createDiningOrder(true); // 团餐
        order.setPax(2);
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 2);
        
        // DIPLOMATIC折扣不支持整单折扣，应该回退到单品折扣
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.DIPLOMATIC, null);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.DINING)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果：应该使用单品折扣逻辑
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("餐饮业态 - 服务费计算")
    public void testDiningWithServiceCharge() {
        SalesOrder order = createDiningOrder(false);
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 1);
        
        Store store = new Store();
        store.setServiceChargeType("PERCENTAGE");
        store.setServiceChargeValue(new BigDecimal("0.10")); // 10%
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(null)
                .store(store)
                .businessType(BusinessType.DINING)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证服务费基础
        assertNotNull(result.getServiceChargeBasis());
    }
    
    @Test
    @DisplayName("餐饮业态 - 混合税率商品（VATABLE + VAT_EXEMPT）")
    public void testDiningMixedVatTypes() {
        SalesOrder order = createDiningOrder(false);
        List<SalesOrderItem> items = new ArrayList<>();
        
        // 含税商品
        SalesOrderItem vatableItem = createItem("Vatable Item", "VATABLE", "NONE", new BigDecimal("112.00"), 1);
        items.add(vatableItem);
        
        // 不含税商品
        SalesOrderItem exemptItem = createItem("Exempt Item", "VAT_EXEMPT", "NONE", new BigDecimal("100.00"), 1);
        items.add(exemptItem);
        
        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(null)
                .store(null)
                .businessType(BusinessType.DINING)
                .build();
        
        CalculationResult result = calculator.calculate(context);
        
        // 验证结果
        assertTrue(result.getTotalVatableSales().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.getTotalVatExemptSales().compareTo(BigDecimal.ZERO) > 0);
    }
    
    // ==================== 辅助方法 ====================
    
    private SalesOrder createDiningOrder(boolean isGroupMeal) {
        SalesOrder order = new SalesOrder();
        order.setBusinessType(BusinessType.DINING.name());
        order.setSiNumber("000000001");
        order.setGroupMeal(isGroupMeal);
        order.setTableName("Table 1");
        order.setBillingNo(1L);
        order.setTrxType("Dine-In(堂食)");
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

