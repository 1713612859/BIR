package com.zuno.bir;

import com.zuno.bir.engine.DiscountEngine;
import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.print.ReceiptPrintingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 零售业态 (RETAIL) 50个全覆盖测试用例集
 * 场景: 含税(VATABLE) & 免税(VAT_EXEMPT) 的各种混合
 * 核心校验: Amount Due, Gov Discount Total, Tax Buckets (Vatable vs Exempt)
 */
public class RetailDiscountExtendedTests {

    private Store store;

    @BeforeEach
    public void setup() {
        store = new Store();
        store.setServiceChargeType("FIXED_AMOUNT");
        store.setBusinessType(BusinessType.RETAIL.name());
        store.setName("Retail Store");
        store.setAddress("123 Main St, Anytown USA, 12345, USA");
        store.setCompanyName("Retail Store");
        store.setVatRegTin("123-456-789-00000");

        store.setServiceChargeValue(BigDecimal.ZERO);
    }

    // --- Helpers ---

    private void assertValues(SalesOrder order, String expectedAmountDue, String expectedGovDiscTotal) {
        BigDecimal actualAmountDue = order.getAmountDue() != null ? order.getAmountDue() : BigDecimal.ZERO;
        BigDecimal actualGovDisc = order.getGovDiscountTotal() != null ? order.getGovDiscountTotal() : BigDecimal.ZERO;
        assertEquals(new BigDecimal(expectedAmountDue), actualAmountDue.setScale(2, RoundingMode.HALF_UP), "Amount Due Mismatch");
        assertEquals(new BigDecimal(expectedGovDiscTotal), actualGovDisc.setScale(2, RoundingMode.HALF_UP), "Gov Disc Total Mismatch");
    }

    private void assertTaxBuckets(SalesOrder order, String vatable, String vatAmt, String exempt) {
        BigDecimal v = order.getVatableSales() != null ? order.getVatableSales() : BigDecimal.ZERO;
        BigDecimal va = order.getVatAmount() != null ? order.getVatAmount() : BigDecimal.ZERO;
        BigDecimal e = order.getVatExemptSales() != null ? order.getVatExemptSales() : BigDecimal.ZERO;
        assertEquals(new BigDecimal(vatable), v.setScale(2, RoundingMode.HALF_UP), "Vatable Sales Mismatch");
        assertEquals(new BigDecimal(vatAmt), va.setScale(2, RoundingMode.HALF_UP), "VAT Amount Mismatch");
        assertEquals(new BigDecimal(exempt), e.setScale(2, RoundingMode.HALF_UP), "Exempt Sales Mismatch");
    }

    // =================================================================
    // Group 1: 含税单品 (VATABLE Singles) - 10 Cases
    // =================================================================

    @Test // 1. 普通商品无折扣
    public void test01_Vatable_None() {
        SalesOrder order = createOrder(createItem("Normal", "112.00", "VATABLE", "NONE"));
        runEngine(order);
        assertValues(order, "112.00", "0.00");
        assertTaxBuckets(order, "100.00", "12.00", "0.00");

        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 2. MGS (SC) -> 免税 + 20%
    public void test02_Vatable_MGS_SC() {
        SalesOrder order = createOrder(createItem("MGS", "112.00", "VATABLE", "MGS"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "80.00", "32.00");
        assertTaxBuckets(order, "0.00", "0.00", "80.00");

        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 3. VBN (SC) -> 含税 + 5%
    public void test03_Vatable_VBN_SC() {
        SalesOrder order = createOrder(createItem("VBN", "112.00", "VATABLE", "VBN"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "107.00", "5.00");
        assertTaxBuckets(order, "100.00", "12.00", "0.00");

        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 4. SP (Solo Parent) -> 免税 + 10%
    public void test04_Vatable_SP() {
        SalesOrder order = createOrder(createItem("SP", "112.00", "VATABLE", "SP"));
        addDiscount(order, "SP");
        runEngine(order);
        assertValues(order, "90.00", "22.00");
        assertTaxBuckets(order, "0.00", "0.00", "90.00");
        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 5. DIPLOMATIC (On MGS Tag) -> 强制免税 + 0%
    public void test05_Vatable_Diplo() {
        SalesOrder order = createOrder(createItem("Diplo", "112.00", "VATABLE", "MGS"));
        addDiscount(order, "DIPLOMATIC");
        runEngine(order);
        assertValues(order, "100.00", "12.00");
        assertTaxBuckets(order, "0.00", "0.00", "100.00");
        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 6. PWD (同 SC)
    public void test06_Vatable_PWD() {
        SalesOrder order = createOrder(createItem("MGS", "112.00", "VATABLE", "MGS"));
        addDiscount(order, "PWD");
        runEngine(order);
        assertValues(order, "80.00", "32.00");
        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 7. NAAC (同 SC)
    public void test07_Vatable_NAAC() {
        SalesOrder order = createOrder(createItem("Athlete", "112.00", "VATABLE", "NAAC"));
        addDiscount(order, "NAAC");
        runEngine(order);
        assertValues(order, "80.00", "32.00");
        ReceiptPrintingService receiptPrintingService = new ReceiptPrintingService();

        System.out.println(receiptPrintingService.generateSalesInvoice(order, store));
    }

    @Test // 8. MOV (同 SC)
    public void test08_Vatable_MOV() {
        SalesOrder order = createOrder(createItem("Valor", "112.00", "VATABLE", "MOV"));
        addDiscount(order, "MOV");
        runEngine(order);
        assertValues(order, "80.00", "32.00");
    }

    @Test // 9. 错配: VBN 商品 + SP 卡 (应无折扣)
    public void test09_Vatable_VBN_SP_Mismatch() {
        SalesOrder order = createOrder(createItem("VBN", "112.00", "VATABLE", "VBN"));
        addDiscount(order, "SP");
        runEngine(order);
        assertValues(order, "112.00", "0.00"); // SP 不支持 VBN
    }

    @Test // 10. 错配: 普通商品 + SC 卡 (应无折扣)
    public void test10_Vatable_None_SC_Mismatch() {
        SalesOrder order = createOrder(createItem("Beer", "112.00", "VATABLE", "NONE"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "112.00", "0.00");
    }

    // =================================================================
    // Group 2: 免税单品 (EXEMPT Singles) - 5 Cases
    // =================================================================

    @Test // 11. 免税品 + 无折扣
    public void test11_Exempt_None() {
        SalesOrder order = createOrder(createItem("Rice", "100.00", "VAT_EXEMPT", "NONE"));
        runEngine(order);
        assertValues(order, "100.00", "0.00");
        assertTaxBuckets(order, "0.00", "0.00", "100.00");
    }

    @Test // 12. 免税品 + MGS (SC) -> 20%
    public void test12_Exempt_MGS() {
        SalesOrder order = createOrder(createItem("Raw", "100.00", "VAT_EXEMPT", "MGS"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "80.00", "20.00");
        assertTaxBuckets(order, "0.00", "0.00", "80.00");
    }

    @Test // 13. 免税品 + VBN (SC) -> 5%
    public void test13_Exempt_VBN() {
        SalesOrder order = createOrder(createItem("Basic", "100.00", "VAT_EXEMPT", "VBN"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "95.00", "5.00");
    }

    @Test // 14. 免税品 + SP -> 10%
    public void test14_Exempt_SP() {
        SalesOrder order = createOrder(createItem("Baby", "100.00", "VAT_EXEMPT", "SP"));
        addDiscount(order, "SP");
        runEngine(order);
        assertValues(order, "90.00", "10.00");
    }

    @Test // 15. 免税品 + Diplo -> 0% (无变化)
    public void test15_Exempt_Diplo() {
        SalesOrder order = createOrder(createItem("Raw", "100.00", "VAT_EXEMPT", "DIPLOMATIC"));
        addDiscount(order, "DIPLOMATIC");
        runEngine(order);
        assertValues(order, "100.00", "0.00");
    }

    // =================================================================
    // Group 3: 促销叠加 (Promo + Gov) - 5 Cases
    // =================================================================

    @Test // 16. MGS Promo: 112 - 12 = 100 Base. Net 89.29.
    public void test16_Vatable_MGS_Promo() {
        SalesOrderItem item = createItem("MGS", "112.00", "VATABLE", "MGS");
        item.setRegularDiscount(new BigDecimal("12.00"));
        SalesOrder order = createOrder(item);
        addDiscount(order, "SC");
        runEngine(order);
        // Disc: 17.86. TaxRed: 10.71. Total Gov: 28.57. Due: 71.43.
        assertValues(order, "71.43", "28.57");
    }

    @Test // 17. VBN Promo: 112 - 12 = 100 Base. Net 89.29.
    public void test17_Vatable_VBN_Promo() {
        SalesOrderItem item = createItem("VBN", "112.00", "VATABLE", "VBN");
        item.setRegularDiscount(new BigDecimal("12.00"));
        SalesOrder order = createOrder(item);
        addDiscount(order, "SC");
        runEngine(order);
        // Disc: 4.46 (5%). TaxRed: 0. Due: 95.54.
        assertValues(order, "95.54", "4.46");
    }

    @Test // 18. Exempt MGS Promo: 112 - 12 = 100 Base.
    public void test18_Exempt_MGS_Promo() {
        SalesOrderItem item = createItem("Exempt", "112.00", "VAT_EXEMPT", "MGS");
        item.setRegularDiscount(new BigDecimal("12.00"));
        SalesOrder order = createOrder(item);
        addDiscount(order, "SC");
        runEngine(order);
        // Disc: 20 (20% of 100). Due: 80.
        assertValues(order, "80.00", "20.00");
    }

    @Test // 19. SP Promo: 112 - 12 = 100 Base. Net 89.29.
    public void test19_Vatable_SP_Promo() {
        SalesOrderItem item = createItem("SP", "112.00", "VATABLE", "SP");
        item.setRegularDiscount(new BigDecimal("12.00"));
        SalesOrder order = createOrder(item);
        addDiscount(order, "SP");
        runEngine(order);
        // Disc: 8.93 (10%). TaxRed: 10.71. Gov: 19.64. Due: 80.36.
        assertValues(order, "80.36", "19.64");
    }

    @Test // 20. Promo Only (No Gov)
    public void test20_Promo_Only() {
        SalesOrderItem item = createItem("MGS", "112.00", "VATABLE", "MGS");
        item.setRegularDiscount(new BigDecimal("12.00"));
        SalesOrder order = createOrder(item);
        runEngine(order);
        assertValues(order, "100.00", "0.00");
    }

    // =================================================================
    // Group 4: 数量与精度 (Qty & Rounding) - 5 Cases
    // =================================================================

    @Test // 21. Qty 2: MGS (224 Gross)
    public void test21_MGS_Qty2() {
        SalesOrderItem item = createItem("MGS", "112.00", "VATABLE", "MGS");
        item.setQuantity(2);
        SalesOrder order = createOrder(item);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 160. Benefit 64.
        assertValues(order, "160.00", "64.00");
    }

    @Test // 22. Qty 2: VBN (224 Gross)
    public void test22_VBN_Qty2() {
        SalesOrderItem item = createItem("VBN", "112.00", "VATABLE", "VBN");
        item.setQuantity(2);
        SalesOrder order = createOrder(item);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 214. Benefit 10.
        assertValues(order, "214.00", "10.00");
    }

    @Test // 23. Small Amount (1.12 Gross)
    public void test23_Small_MGS() {
        SalesOrder order = createOrder(createItem("Candy", "1.12", "VATABLE", "MGS"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "0.80", "0.32");
    }

    @Test // 24. Zero Amount
    public void test24_Zero_MGS() {
        SalesOrder order = createOrder(createItem("Free", "0.00", "VATABLE", "MGS"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "0.00", "0.00");
    }

    @Test // 25. Large Amount (1.12M Gross)
    public void test25_Large_MGS() {
        SalesOrder order = createOrder(createItem("Car", "1120000.00", "VATABLE", "MGS"));
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "800000.00", "320000.00");
    }

    // =================================================================
    // Group 5: 双品混合 (Mixed 2 Items) - 10 Cases
    // =================================================================

    @Test // 26. Vatable(MGS) + Vatable(VBN) -> 黄金混合
    public void test26_Mixed_MGS_VBN() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("VBN", "112.00", "VATABLE", "VBN");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 80 + 107 = 187. Gov: 32 + 5 = 37.
        assertValues(order, "187.00", "37.00");
        assertTaxBuckets(order, "100.00", "12.00", "80.00");
    }

    @Test // 27. Vatable(MGS) + Vatable(NONE)
    public void test27_Mixed_MGS_None() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("None", "112.00", "VATABLE", "NONE");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 80 + 112 = 192. Gov 32.
        assertValues(order, "192.00", "32.00");
        assertTaxBuckets(order, "100.00", "12.00", "80.00");
    }

    @Test // 28. Vatable(VBN) + Vatable(NONE)
    public void test28_Mixed_VBN_None() {
        SalesOrderItem i1 = createItem("VBN", "112.00", "VATABLE", "VBN");
        SalesOrderItem i2 = createItem("None", "112.00", "VATABLE", "NONE");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 107 + 112 = 219. Gov 5.
        assertValues(order, "219.00", "5.00");
        assertTaxBuckets(order, "200.00", "24.00", "0.00");
    }

    @Test // 29. Vatable(SP) + Vatable(NONE)
    public void test29_Mixed_SP_None() {
        SalesOrderItem i1 = createItem("SP", "112.00", "VATABLE", "SP");
        SalesOrderItem i2 = createItem("None", "112.00", "VATABLE", "NONE");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SP");
        runEngine(order);
        // Pay 90 + 112 = 202. Gov 22.
        assertValues(order, "202.00", "22.00");
        assertTaxBuckets(order, "100.00", "12.00", "90.00");
    }

    @Test // 30. Vatable(MGS) + Exempt(MGS)
    public void test30_Mixed_MGS_ExemptMGS() {
        SalesOrderItem i1 = createItem("V_MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("E_MGS", "100.00", "VAT_EXEMPT", "MGS");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 80 + 80 = 160. Gov 32 + 20 = 52.
        assertValues(order, "160.00", "52.00");
        assertTaxBuckets(order, "0.00", "0.00", "160.00");
    }

    @Test // 31. Vatable(VBN) + Exempt(VBN)
    public void test31_Mixed_VBN_ExemptVBN() {
        SalesOrderItem i1 = createItem("V_VBN", "112.00", "VATABLE", "VBN");
        SalesOrderItem i2 = createItem("E_VBN", "100.00", "VAT_EXEMPT", "VBN");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 107 + 95 = 202. Gov 5 + 5 = 10.
        assertValues(order, "202.00", "10.00");
        assertTaxBuckets(order, "100.00", "12.00", "95.00");
    }

    @Test // 32. Vatable(MGS) + Exempt(VBN) -> 交叉类型
    public void test32_Mixed_MGS_ExemptVBN() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("VBN", "100.00", "VAT_EXEMPT", "VBN");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 80 + 95 = 175. Gov 32 + 5 = 37.
        assertValues(order, "175.00", "37.00");
        assertTaxBuckets(order, "0.00", "0.00", "175.00");
    }

    @Test // 33. Vatable(SP) + Exempt(SP)
    public void test33_Mixed_SP_ExemptSP() {
        SalesOrderItem i1 = createItem("V_SP", "112.00", "VATABLE", "SP");
        SalesOrderItem i2 = createItem("E_SP", "100.00", "VAT_EXEMPT", "SP");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SP");
        runEngine(order);
        // Pay 90 + 90 = 180. Gov 22 + 10 = 32.
        assertValues(order, "180.00", "32.00");
        assertTaxBuckets(order, "0.00", "0.00", "180.00");
    }

    @Test // 34. 指定 ID 匹配 (I1 有折, I2 无折)
    public void test34_Specific_ID() {
        SalesOrderItem i1 = createItem("I1", "112.00", "VATABLE", "MGS");
        i1.setItemId(101L);
        SalesOrderItem i2 = createItem("I2", "112.00", "VATABLE", "MGS");
        i2.setItemId(102L);

        SalesOrder order = createOrder(i1, i2);
        order.setGovDiscounts(Collections.singletonList(createDiscount(101L, "SC")));

        runEngine(order);
        // I1 Pay 80. I2 Pay 112. Total 192.
        assertValues(order, "192.00", "32.00");
    }

    @Test // 35. Diplomatic 覆盖 VBN + MGS (双含税)
    public void test35_Diplo_Override_Two() {
        SalesOrderItem i1 = createItem("VBN", "112.00", "VATABLE", "VBN");
        SalesOrderItem i2 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "DIPLOMATIC");
        runEngine(order);
        // Both forced Exempt + 0% Disc.
        // Pay 100 + 100 = 200. Gov 12 + 12 = 24.
        assertValues(order, "200.00", "24.00");
    }

    // =================================================================
    // Group 6: 复杂混合 (Mixed 3+ Items) & 边界 - 15 Cases
    // =================================================================

    @Test // 36. MGS + VBN + NONE
    public void test36_Mixed_3_Types() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("VBN", "112.00", "VATABLE", "VBN");
        SalesOrderItem i3 = createItem("NONE", "112.00", "VATABLE", "NONE");
        SalesOrder order = createOrder(i1, i2, i3);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 80 + 107 + 112 = 299. Gov 32 + 5 = 37.
        assertValues(order, "299.00", "37.00");
    }

    @Test // 37. Exempt(MGS) + Exempt(VBN) + Exempt(None)
    public void test37_Exempt_3_Types() {
        SalesOrderItem i1 = createItem("E_MGS", "100.00", "VAT_EXEMPT", "MGS");
        SalesOrderItem i2 = createItem("E_VBN", "100.00", "VAT_EXEMPT", "VBN");
        SalesOrderItem i3 = createItem("E_None", "100.00", "VAT_EXEMPT", "NONE");
        SalesOrder order = createOrder(i1, i2, i3);
        addDiscount(order, "SC");
        runEngine(order);
        // Pay 80 + 95 + 100 = 275. Gov 20 + 5 = 25.
        assertValues(order, "275.00", "25.00");
    }

    @Test // 38. Diplomatic 作用于 3种商品 (MGS+VBN+NONE)
    public void test38_Diplo_3_Types_Tagged() {
        // Diplo 只作用于 Tagged items (MGS/VBN/DIPLOMATIC). NONE tag 不生效.
        // I1(MGS) -> 100. I2(VBN) -> 100. I3(NONE) -> 112.
        // Total 312.
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("VBN", "112.00", "VATABLE", "VBN");
        SalesOrderItem i3 = createItem("NONE", "112.00", "VATABLE", "NONE");
        SalesOrder order = createOrder(i1, i2, i3);
        addDiscount(order, "DIPLOMATIC");
        runEngine(order);
        assertValues(order, "300.00", "36.00");
    }

    @Test // 39. Vatable(MGS) + Vatable(MGS) + Exempt(MGS)
    public void test39_All_MGS_Mixed_Tax() {
        SalesOrderItem i1 = createItem("V1", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("V2", "112.00", "VATABLE", "MGS");
        SalesOrderItem i3 = createItem("E1", "100.00", "VAT_EXEMPT", "MGS");
        SalesOrder order = createOrder(i1, i2, i3);
        addDiscount(order, "SC");
        runEngine(order);
        // 80 + 80 + 80 = 240. Gov 32 + 32 + 20 = 84.
        assertValues(order, "240.00", "84.00");
    }

    @Test // 40. Promo on MGS + Promo on VBN
    public void test40_Mixed_Promo() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        i1.setRegularDiscount(new BigDecimal("12.00")); // -> 71.43
        SalesOrderItem i2 = createItem("VBN", "112.00", "VATABLE", "VBN");
        i2.setRegularDiscount(new BigDecimal("12.00")); // -> 95.54
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // 71.43 + 95.54 = 166.97. Gov 28.57 + 4.46 = 33.03.
        assertValues(order, "166.97", "33.03");
    }

    @Test // 41. 混合 ID 匹配 (One Specific, One Global?)
    public void test41_Specific_And_Global() {
        // Engine typically takes first applicable.
        // If we pass 2 discounts, one specific, one global.
        SalesOrderItem i1 = createItem("Target", "112.00", "VATABLE", "MGS");
        i1.setItemId(555L);
        SalesOrderItem i2 = createItem("Other", "112.00", "VATABLE", "MGS");
        i2.setItemId(666L);

        SalesOrder order = createOrder(i1, i2);
        List<GovernmentDiscount> discs = new ArrayList<>();
        discs.add(createDiscount(555L, "SC")); // Specific for I1
        discs.add(createDiscount(null, "PWD")); // Global PWD
        order.setGovDiscounts(discs);

        runEngine(order);
        // I1 -> SC (80). I2 -> PWD (80).
        // Total 160.
        assertValues(order, "160.00", "64.00");
    }

    @Test // 42. Vatable(MGS) + Vatable(SP) 使用 SC 卡
    public void test42_MGS_SP_with_SC() {
        // SC Card applies to MGS, but NOT to SP tag.
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("SP", "112.00", "VATABLE", "SP");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // I1 -> 80. I2 -> 112. Total 192.
        assertValues(order, "192.00", "32.00");
    }

    @Test // 43. Vatable(MGS) + Vatable(SP) 使用 SP 卡
    public void test43_MGS_SP_with_SP() {
        // SP Card applies to SP tag, but NOT to MGS tag.
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("SP", "112.00", "VATABLE", "SP");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SP");
        runEngine(order);
        // I1 -> 112. I2 -> 90. Total 202.
        assertValues(order, "202.00", "22.00");
    }

    @Test // 44. Vatable(MGS) + Vatable(SP) 传入双卡 (Mixed IDs)
    public void test44_Dual_Cards_Specific() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        i1.setItemId(1L);
        SalesOrderItem i2 = createItem("SP", "112.00", "VATABLE", "SP");
        i2.setItemId(2L);

        SalesOrder order = createOrder(i1, i2);
        List<GovernmentDiscount> discs = new ArrayList<>();
        discs.add(createDiscount(1L, "SC"));
        discs.add(createDiscount(2L, "SP"));
        order.setGovDiscounts(discs);

        runEngine(order);
        // 80 + 90 = 170.
        assertValues(order, "170.00", "54.00");
    }

    @Test // 45. Exempt(MGS) + Exempt(VBN) + Exempt(SP) + SC
    public void test45_Three_Exempt_SC() {
        // SC only applies to MGS and VBN. Ignores SP.
        SalesOrderItem i1 = createItem("MGS", "100.00", "VAT_EXEMPT", "MGS");
        SalesOrderItem i2 = createItem("VBN", "100.00", "VAT_EXEMPT", "VBN");
        SalesOrderItem i3 = createItem("SP", "100.00", "VAT_EXEMPT", "SP");
        SalesOrder order = createOrder(i1, i2, i3);
        addDiscount(order, "SC");
        runEngine(order);
        // 80 + 95 + 100 = 275. Gov 25.
        assertValues(order, "275.00", "25.00");
    }

    @Test // 46. Qty Scaling Mixed: 2xMGS + 2xVBN
    public void test46_Scaling_Mixed() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS");
        i1.setQuantity(2);
        SalesOrderItem i2 = createItem("VBN", "112.00", "VATABLE", "VBN");
        i2.setQuantity(2);
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "SC");
        runEngine(order);
        // 160 + 214 = 374. Gov 64 + 10 = 74.
        assertValues(order, "374.00", "74.00");
    }

    @Test // 47. Promo + Qty Scaling
    public void test47_Promo_Scaling() {
        // Unit 112. Qty 2. Total Gross 224.
        // Total Promo 24. Base 200.
        // Net 178.57. VAT 21.43.
        // TaxRed 21.43. Disc 35.71 (20% of Net).
        // Pay 200 - 21.43 - 35.71 = 142.86.
        // GovDisc 57.14.
        SalesOrderItem item = createItem("MGS", "112.00", "VATABLE", "MGS");
        item.setQuantity(2);
        item.setRegularDiscount(new BigDecimal("24.00")); // Total discount for line
        SalesOrder order = createOrder(item);
        addDiscount(order, "SC");
        runEngine(order);
        assertValues(order, "142.86", "57.14");
    }

    @Test // 48. Rounding Edge Case: 3 Items, 1.12 each
    public void test48_Rounding_Multiple() {
        SalesOrderItem i1 = createItem("I1", "1.12", "VATABLE", "MGS");
        SalesOrderItem i2 = createItem("I2", "1.12", "VATABLE", "MGS");
        SalesOrderItem i3 = createItem("I3", "1.12", "VATABLE", "MGS");
        SalesOrder order = createOrder(i1, i2, i3);
        addDiscount(order, "SC");
        runEngine(order);
        // 0.80 * 3 = 2.40.
        assertValues(order, "2.40", "0.96");
    }

    @Test // 49. Vatable(VBN) + Exempt(MGS) + Diplo
    public void test49_Diplo_Override_Mixed_Tax() {
        // I1(VBN Vatable): 112 -> 100 (Exempt, 0% Disc)
        // I2(MGS Exempt): 100 -> 100 (Exempt, 0% Disc)
        // Total 200.
        SalesOrderItem i1 = createItem("VBN", "112.00", "VATABLE", "VBN");
        SalesOrderItem i2 = createItem("MGS", "100.00", "VAT_EXEMPT", "MGS");
        SalesOrder order = createOrder(i1, i2);
        addDiscount(order, "DIPLOMATIC");
        runEngine(order);
        assertValues(order, "200.00", "12.00");
    }

    @Test // 50. Massive Mixed Basket
    public void test50_Massive_Mixed() {
        SalesOrderItem i1 = createItem("MGS", "112.00", "VATABLE", "MGS"); // -> 80
        SalesOrderItem i2 = createItem("VBN", "112.00", "VATABLE", "VBN"); // -> 107
        SalesOrderItem i3 = createItem("SP", "112.00", "VATABLE", "SP");   // -> 112 (SC no effect)
        SalesOrderItem i4 = createItem("None", "112.00", "VATABLE", "NONE");// -> 112
        SalesOrderItem i5 = createItem("E_MGS", "100.00", "VAT_EXEMPT", "MGS"); // -> 80

        SalesOrder order = createOrder(i1, i2, i3, i4, i5);
        addDiscount(order, "SC");
        runEngine(order);

        // 80 + 107 + 112 + 112 + 80 = 491.
        // Gov: 32 + 5 + 0 + 0 + 20 = 57.
        assertValues(order, "491.00", "57.00");
    }

    // --- Creation Helpers ---

    private void runEngine(SalesOrder order) {
        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);
    }

    private SalesOrder createOrder(SalesOrderItem... items) {
        SalesOrder order = new SalesOrder();
        order.setBusinessType(BusinessType.RETAIL.name());
        order.setGroupMeal(false);
        order.setItems(Arrays.asList(items));
        order.setServiceCharge(BigDecimal.ZERO);
        return order;
    }

    private SalesOrderItem createItem(String name, String price, String vatType, String tag) {
        SalesOrderItem item = new SalesOrderItem();
        item.setItemId(Math.round(Math.random() * 10000));
        item.setDescription(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(1);
        item.setVatType(vatType);
        item.setDiscountTag(tag);
        item.setRegularDiscount(BigDecimal.ZERO);
        return item;
    }

    private void addDiscount(SalesOrder order, String type) {
        order.setGovDiscounts(Collections.singletonList(createDiscount(null, type)));
    }

    private GovernmentDiscount createDiscount(Long itemId, String type) {
        GovernmentDiscount gd = new GovernmentDiscount();
        gd.setItemId(itemId);
        gd.setPersonType(type);
        gd.setCount(1);
        gd.setLessVat(BigDecimal.ZERO);
        gd.setDiscount(BigDecimal.ZERO);
        gd.setAddVat(BigDecimal.ZERO);
        return gd;
    }
}