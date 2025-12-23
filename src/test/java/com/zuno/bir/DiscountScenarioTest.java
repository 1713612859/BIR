//package com.zuno.bir;
//
//import com.zuno.bir.engine.DiscountEngine;
//import com.zuno.bir.entity.GovernmentDiscount;
//import com.zuno.bir.entity.SalesOrder;
//import com.zuno.bir.entity.SalesOrderItem;
//import com.zuno.bir.entity.Store;
//import com.zuno.bir.enums.BusinessType;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//import java.util.Arrays;
//
///**
// * 自动化场景测试 (覆盖 VBN 修正逻辑)
// */
//public class DiscountScenarioTest {
//
//    // ==========================================
//    // 场景 1: 零售业态 - SC 购买一般商品 (MGS)
//    // 预期：去税 -> 20%折扣 -> 免税
//    // Result: 112 -> 100(Net) -> 20(Disc) -> 80(Payable, Exempt)
//    // ==========================================
//    @Test
//    public void testRetail_SC_MGS() {
//        System.out.println("\n========== 测试场景 1: 零售 SC (MGS 20%) ==========");
//        SalesOrderItem item = createItem(1L, "Medicine", "112.00", "MGS");
//        SalesOrder order = createOrder(BusinessType.RETAIL, false);
//        GovernmentDiscount discount = createDiscount(1L, "SC");
//
//        DiscountEngine.calculateOrder(order, Arrays.asList(item), Arrays.asList(discount), new Store());
//
//        assertAmount(order.getAmountDue(), "80.00");
//        assertAmount(order.getGovDiscountTotal(), "20.00");
//        assertAmount(order.getVatAmount(), "0.00");
//        assertAmount(order.getVatExemptSales(), "80.00");
//        System.out.println("✅ 场景 1 通过: 最终金额 80.00 (免税)");
//    }
//
//    // ==========================================
//    // 场景 2: 零售业态 - SC 购买必需品 (VBN)
//    // 预期：5% 折扣 (基于 Net)，但不免税 (含税)
//    // Result:
//    //   Gross: 112.00
//    //   Base Net: 100.00
//    //   Discount: 100 * 0.05 = 5.00
//    //   Payable: 112.00 - 5.00 = 107.00
//    //   VAT Analysis: 107.00 is VAT Inclusive.
//    // ==========================================
//    @Test
//    public void testRetail_SC_VBN() {
//        System.out.println("\n========== 测试场景 2: 零售 SC (VBN 5%) ==========");
//        SalesOrderItem item = createItem(1L, "Rice", "112.00", "VBN");
//        SalesOrder order = createOrder(BusinessType.RETAIL, false);
//        GovernmentDiscount discount = createDiscount(1L, "SC");
//
//        DiscountEngine.calculateOrder(order, Arrays.asList(item), Arrays.asList(discount),);
//
//        assertAmount(order.getAmountDue(), "107.00");
//        assertAmount(order.getGovDiscountTotal(), "5.00");
//
//        // 关键验证：它不应该在 Exempt 里，而应该在 Vatable 里
//        assertAmount(order.getVatExemptSales(), "0.00");
//
//        // 107 / 1.12 = 95.54 (Vatable Net)
//        assertAmount(order.getVatableSales(), "95.54");
//        // 107 - 95.54 = 11.46 (VAT Amount)
//        assertAmount(order.getVatAmount(), "11.46");
//
//        System.out.println("✅ 场景 2 通过: VBN 5% 折扣且含税 (107.00)");
//    }
//
//    // ==========================================
//    // 场景 3: 餐饮业态 - 团餐分摊
//    // ==========================================
//    @Test
//    public void testDining_GroupMeal_Sharing() {
//        System.out.println("\n========== 测试场景 3: 餐饮团餐分摊 (5人/1SC/1PWD) ==========");
//        // 560 Total -> 112 Per Pax (100 Net)
//        SalesOrderItem item = createItem(1L, "Platter", "560.00", "MGS");
//        SalesOrder order = createOrder(BusinessType.DINING, true);
//        order.setPax(5);
//
//        GovernmentDiscount d1 = createDiscount(null, "SC");
//        d1.setCount(1);
//        GovernmentDiscount d2 = createDiscount(null, "PWD");
//        d2.setCount(1);
//
//        DiscountEngine.calculateOrder(order, Arrays.asList(item), Arrays.asList(d1, d2), new Store());
//
//        // SC(1): 100 Net -> 20 Disc -> 80 Pay
//        // PWD(1): 100 Net -> 20 Disc -> 80 Pay
//        // Regular(3): 3 * 112 = 336 Pay
//        // Total: 80+80+336 = 496
//
//        assertAmount(order.getAmountDue(), "496.00");
//        assertAmount(order.getGovDiscountTotal(), "40.00");
//        assertAmount(order.getVatAmount(), "36.00"); // 300 * 0.12
//
//        System.out.println("✅ 场景 3 通过: 团餐分摊计算正确");
//    }
//
//    // ==========================================
//    // 场景 4: 快餐业态 - 互斥测试 (SP)
//    // ==========================================
//    @Test
//    public void testFastFood_SP_Rejection() {
//        System.out.println("\n========== 测试场景 4: 快餐业态禁用 SP 折扣 ==========");
//        SalesOrderItem item = createItem(1L, "Meal", "112.00", "SP");
//        SalesOrder order = createOrder(BusinessType.FAST_FOOD, false);
//        GovernmentDiscount discount = createDiscount(1L, "SP");
//
//        DiscountEngine.calculateOrder(order, Arrays.asList(item), Arrays.asList(discount));
//
//        // 拒绝折扣，原价 112
//        assertAmount(order.getAmountDue(), "112.00");
//        assertAmount(order.getGovDiscountTotal(), "0.00");
//
//        System.out.println("✅ 场景 4 通过: 快餐业态成功拦截 SP 折扣");
//    }
//
//    // ==========================================
//    // 辅助工具方法
//    // ==========================================
//    private SalesOrderItem createItem(Long id, String name, String priceStr, String tag) {
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(id);
//        item.setDescription(name);
//        item.setUnitPrice(new BigDecimal(priceStr));
//        item.setQuantity(1);
//        item.setVatType("VATABLE");
//        item.setDiscountTag(tag);
//        item.setRegularDiscount(BigDecimal.ZERO);
//        return item;
//    }
//
//    private SalesOrder createOrder(BusinessType type, boolean isGroup) {
//        SalesOrder order = new SalesOrder();
//        order.setBusinessType(type.name());
//        order.setGroupMeal(isGroup);
//        order.setServiceCharge(BigDecimal.ZERO);
//        return order;
//    }
//
//    private GovernmentDiscount createDiscount(Long itemId, String type) {
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(itemId);
//        gd.setPersonType(type);
//        gd.setCount(1);
//        return gd;
//    }
//
//    private void assertAmount(BigDecimal actual, String expectedStr) {
//        BigDecimal expected = new BigDecimal(expectedStr);
//        if(actual == null) actual = BigDecimal.ZERO;
//        if(actual.subtract(expected).abs().compareTo(new BigDecimal("0.01")) > 0) {
//            throw new AssertionError("金额不匹配! 预期: " + expectedStr + ", 实际: " + actual);
//        }
//    }
//}