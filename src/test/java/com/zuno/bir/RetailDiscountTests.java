package com.zuno.bir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuno.bir.engine.DiscountEngine;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 零售业态 (RETAIL) 完整测试用例集 - 最终修正版
 * 核心逻辑校验: Amount Due = Gross - TaxReduction - Discount
 * 关键修正: GovDiscountTotal = TaxReduction + Discount
 */
public class RetailDiscountTests {

    private static final ObjectMapper mapper = new ObjectMapper();

    // 精度比较函数
    private static void assertAmount(String message, BigDecimal actual, String expected) {
        if(actual == null) actual = BigDecimal.ZERO;
        assertEquals(new BigDecimal(expected), actual.setScale(2, RoundingMode.HALF_UP), message);
    }

    // =================================================================
    // Case 1: MGS 标准流程 (SC/PWD/NAAC/MOV)
    // =================================================================
    @Test
    public void testRetail_MGS_Standard() throws JsonProcessingException {
        // Item: 1120.00 (VAT Inclusive)
        // 1. De-VAT: Net = 1000.00, VAT = 120.00
        // 2. Tax Reduction (LessVAT): 120.00 (MGS 免税)
        // 3. Discount (20% of Net): 1000 * 0.20 = 200.00
        // 4. Final Amount: 1120 - 120 - 200 = 800.00

        // 校验恒等式: Gross(1120) - GovDisc(320) = AmountDue(800)

        System.out.println("\n--- Case 1: MGS Standard (SC/PWD) ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem item = mockItem(1L, "General Goods", "1120.00", 1, "MGS");
        order.setItems(Collections.singletonList(item));
        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "SC")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        System.out.println("Result: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(order));

        assertAmount("Amount Due", order.getAmountDue(), "800.00");
        // 【关键修正】: GovDisc = Discount(200) + LessVAT(120) = 320
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "320.00");

        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "800.00");
        assertAmount("Vatable Sales", order.getVatableSales(), "0.00");
        assertAmount("VAT Amount", order.getVatAmount(), "0.00");

        // 小票显示
        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "120.00");
        assertAmount("Discount", order.getGovDiscounts().get(0).getDiscount(), "200.00");
    }

    // =================================================================
    // Case 2: VBN 标准流程 (Basic Necessities)
    // =================================================================
    @Test
    public void testRetail_VBN_Standard() {
        // Item: 320.00 (VAT Inclusive)
        // 1. De-VAT: Net = 285.71, VAT = 34.29
        // 2. Tax Reduction: 0.00 (VBN 不免税)
        // 3. Discount (5%): 285.71 * 0.05 = 14.29
        // 4. Final Amount: 320 - 0 - 14.29 = 305.71

        // 校验恒等式: Gross(320) - GovDisc(14.29) = AmountDue(305.71)

        System.out.println("\n--- Case 2: VBN Standard (5% Taxable) ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem item = mockItem(1L, "Basic Goods", "320.00", 1, "VBN");
        order.setItems(Collections.singletonList(item));
        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "SC")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        assertAmount("Amount Due", order.getAmountDue(), "305.71");
        // VBN 没有免税，只有折扣
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "14.29");

        // VBN 特殊归集：使用原始值
        assertAmount("Vatable Sales", order.getVatableSales(), "285.71");
        assertAmount("VAT Amount", order.getVatAmount(), "34.29");
        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "0.00");

        // 小票显示
        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "0.00");
    }

    // =================================================================
    // Case 3: 混合订单 (MGS + VBN) - 黄金测试用例
    // =================================================================
    @Test
    public void testRetail_Mixed_MGS_VBN() {
        // Item A (MGS): Gross 1120. TaxRed 120. Disc 200. Pay 800.
        // Item B (VBN): Gross 320. TaxRed 0. Disc 14.29. Pay 305.71.

        // Total Gross: 1440.00
        // Total TaxRed: 120.00
        // Total Disc: 214.29
        // Total GovDiscBenefit: 334.29
        // Amount Due: 1440 - 334.29 = 1105.71

        System.out.println("\n--- Case 3: Mixed MGS + VBN ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem itemA = mockItem(1L, "MGS Item", "1120.00", 1, "MGS");
        SalesOrderItem itemB = mockItem(2L, "VBN Item", "320.00", 1, "VBN");
        order.setItems(Arrays.asList(itemA, itemB));

        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "SC")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        assertAmount("Amount Due", order.getAmountDue(), "1105.71");
        // 【关键修正】: 200(MGS Disc) + 14.29(VBN Disc) + 120(MGS TaxRed) = 334.29
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "334.29");

        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "800.00");
        assertAmount("Vatable Sales", order.getVatableSales(), "285.71");
        assertAmount("VAT Amount", order.getVatAmount(), "34.29");

        // 小票显示
        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "120.00"); // 仅 MGS 的税
    }

    // =================================================================
    // Case 4: Solo Parent (SP) - 10% 折扣 + 免税
    // =================================================================
    @Test
    public void testRetail_SoloParent_SP() {
        // Item: 1120.00
        // 1. De-VAT: Net = 1000.00, VAT = 120.00
        // 2. Tax Reduction: 120.00
        // 3. Discount (10%): 1000 * 0.10 = 100.00
        // 4. Final: 1120 - 120 - 100 = 900.00

        // GovDisc = 120 + 100 = 220

        System.out.println("\n--- Case 4: Solo Parent (SP) ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem item = mockItem(1L, "Child Care", "1120.00", 1, "SP");
        order.setItems(Collections.singletonList(item));
        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "SP")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        assertAmount("Amount Due", order.getAmountDue(), "900.00");
        // 【关键修正】: 100(Disc) + 120(TaxRed) = 220
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "220.00");
        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "900.00");

        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "120.00");
    }

    // =================================================================
    // Case 5: Diplomatic (外交官) - 0% 折扣 + 免税
    // =================================================================
    @Test
    public void testRetail_Diplomatic() {
        // Item: 1120.00
        // 1. De-VAT: Net = 1000.00, VAT = 120.00
        // 2. Tax Reduction: 120.00
        // 3. Discount (0%): 0.00
        // 4. Final: 1120 - 120 - 0 = 1000.00

        // GovDisc = 120 + 0 = 120

        System.out.println("\n--- Case 5: Diplomatic ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem item = mockItem(1L, "Luxury Goods", "1120.00", 1, "DIPLOMATIC");
        order.setItems(Collections.singletonList(item));
        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "DIPLOMATIC")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        assertAmount("Amount Due", order.getAmountDue(), "1000.00");
        // 【关键修正】: 仅免税，无折扣 -> Total Benefit = 120
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "120.00");
        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "1000.00");

        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "120.00");
    }

    // =================================================================
    // Case 6: 叠加常规折扣 (Promo + SC)
    // =================================================================
    @Test
    public void testRetail_WithRegularDiscount() {
        // Item: 1120.00, Promo: 120.00
        // 1. Base Gross: 1120 - 120 = 1000.00
        // 2. De-VAT: Net = 892.86, VAT = 107.14
        // 3. Tax Reduction: 107.14
        // 4. Discount (20% of 892.86): 178.57
        // 5. Final: 1000 - 107.14 - 178.57 = 714.29

        // Gross (1120) - RegDisc(120) - GovBenefit(107.14+178.57) = 714.29

        System.out.println("\n--- Case 6: Regular Discount + SC ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem item = mockItem(1L, "Promo Item", "1120.00", 1, "MGS");
        item.setRegularDiscount(new BigDecimal("120.00"));
        order.setItems(Collections.singletonList(item));

        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "SC")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        assertAmount("Amount Due", order.getAmountDue(), "714.29");
        // 【关键修正】: 178.57(Disc) + 107.14(TaxRed) = 285.71
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "285.71");
        System.out.println("order = " + order.getGovDiscounts());
        assertAmount("Regular Discount", order.getRegularDiscount(), "120.00");
        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "714.29");

        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "107.14");
    }

    // =================================================================
    // Case 7: 本身无税商品 (VAT_EXEMPT Item) + SC
    // =================================================================
    @Test
    public void testRetail_VatExemptItem_SC() {
        // Item: 1000.00 (VAT Type = VAT_EXEMPT)
        // 1. De-VAT: Net = 1000.00, VAT = 0.00
        // 2. Tax Reduction: 0.00 (无税可免)
        // 3. Discount (20%): 200.00
        // 4. Final: 1000 - 0 - 200 = 800.00

        // GovDisc = 200 + 0 = 200

        System.out.println("\n--- Case 7: VAT_EXEMPT Item + SC ---");
        Store store = mockStore();
        SalesOrder order = mockOrder(BusinessType.RETAIL);

        SalesOrderItem item = mockItem(1L, "Raw Food", "1000.00", 1, "MGS");
        item.setVatType("VAT_EXEMPT");
        order.setItems(Collections.singletonList(item));

        order.setGovDiscounts(Collections.singletonList(mockDiscount(null, "SC")));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        assertAmount("Amount Due", order.getAmountDue(), "800.00");
        assertAmount("Gov Discount Total", order.getGovDiscountTotal(), "200.00");
        assertAmount("VAT Exempt Sales", order.getVatExemptSales(), "800.00");

        assertAmount("Less VAT", order.getGovDiscounts().get(0).getLessVat(), "0.00");
    }




    // --- Test Helpers ---

    private SalesOrderItem mockItem(Long id, String name, String priceStr, int qty, String tag) {
        SalesOrderItem item = new SalesOrderItem();
        item.setItemId(id);
        item.setDescription(name);
        item.setUnitPrice(new BigDecimal(priceStr));
        item.setQuantity(qty);
        item.setVatType("VATABLE");
        item.setDiscountTag(tag);
        item.setRegularDiscount(BigDecimal.ZERO);
        return item;
    }

    private SalesOrder mockOrder(BusinessType type) {
        SalesOrder order = new SalesOrder();
        order.setBusinessType(type.name());
        order.setGroupMeal(false);
        order.setServiceCharge(BigDecimal.ZERO);
        return order;
    }

    private GovernmentDiscount mockDiscount(Long itemId, String type) {
        GovernmentDiscount gd = new GovernmentDiscount();
        gd.setItemId(itemId);
        gd.setPersonType(type);
        gd.setCount(1);
        gd.setLessVat(BigDecimal.ZERO);
        gd.setDiscount(BigDecimal.ZERO);
        gd.setAddVat(BigDecimal.ZERO);
        return gd;
    }

    private Store mockStore() {
        Store store = new Store();
        store.setServiceChargeType("FIXED_AMOUNT");
        store.setServiceChargeValue(BigDecimal.ZERO);
        return store;
    }
}