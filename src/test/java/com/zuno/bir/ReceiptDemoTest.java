package com.zuno.bir.test;

import com.zuno.bir.engine.DiscountEngine;
import com.zuno.bir.entity.*;
import com.zuno.bir.print.ReceiptPrinter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

public class ReceiptDemoTest {

    // =================================================================
    // 场景 1: 餐饮 (Dining) - SC 折扣 + 10% 服务费
    // =================================================================
    @Test
    public void testDining_Mixed_SC_WithServiceCharge() {
        System.out.println("========= 场景 1: 餐饮 | 牛排(SC) + 拿铁 | 服务费 10% =========");

        // 1. 带 10% 服务费的门店
        Store store = mockStoreWithSC();
        Device device = mockDevice();
        User cashier = mockUser();

        SalesOrder order = new SalesOrder();
        order.setBusinessType("DINING");
        order.setTrxType("Dine-In");
        order.setSiNumber("SI 000012345");
        order.setTableName("Table 1");
        order.setPax(3);

        // Item 1: Steak (SC, Exempt)
        SalesOrderItem item1 = new SalesOrderItem();
        item1.setItemId(1L);
        item1.setDescription("Steak");
        item1.setUnitPrice(new BigDecimal("1120.00"));
        item1.setQuantity(1);
        item1.setVatType("VATABLE");
        item1.setDiscountTag("MGS");

        // Item 2: Latte (Normal)
        SalesOrderItem item2 = new SalesOrderItem();
        item2.setItemId(2L);
        item2.setDescription("Latte");
        item2.setUnitPrice(new BigDecimal("160.00"));
        item2.setQuantity(2);
        item2.setVatType("VATABLE");
        item2.setDiscountTag("NONE");

        order.setItems(Arrays.asList(item1, item2));

        GovernmentDiscount gd = new GovernmentDiscount();
        gd.setItemId(1L);
        gd.setPersonType("SC");
        order.setGovDiscounts(Collections.singletonList(gd));

        // 计算
        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        // 打印
        String receipt = ReceiptPrinter.generateReceipt(store, device, order, cashier, "Cashier Copy");
        System.out.println(receipt);
    }

    // =================================================================
    // 场景 2: 零售 (Retail) - VBN 折扣 (含税)
    // =================================================================
    @Test
    public void testRetail_VBN_Taxable() {
        System.out.println("\n========= 场景 2: 零售 | 香米(VBN) | 含税折扣 =========");

        // 2. 无服务费的门店
        Store store = mockStoreNoSC();
        Device device = mockDevice();
        User cashier = mockUser();

        SalesOrder order = new SalesOrder();
        order.setBusinessType("RETAIL");
        order.setSiNumber("SI 99999");

        SalesOrderItem item = new SalesOrderItem();
        item.setItemId(101L);
        item.setDescription("Rice 5kg");
        item.setUnitPrice(new BigDecimal("560.00"));
        item.setQuantity(2);
        item.setVatType("VATABLE");
        item.setDiscountTag("VBN");

        order.setItems(Collections.singletonList(item));

        GovernmentDiscount gd = new GovernmentDiscount();
        gd.setItemId(101L);
        gd.setPersonType("SC");
        order.setGovDiscounts(Collections.singletonList(gd));

        DiscountEngine.calculateOrder(order, order.getItems(), order.getGovDiscounts(), store);

        // 模拟支付
        order.setAmountDue(order.getAmountDue()); // 确保 AmountDue 已计算

        String receipt = ReceiptPrinter.generateReceipt(store, device, order, cashier, "Customer Copy");
        System.out.println(receipt);
    }

    // --- Helpers ---
    private Store mockStoreWithSC() {
        Store s = new Store();
        s.setName("ZUNO CAFE");
        s.setVatRegTin("123");
        s.setServiceChargeType("PERCENTAGE");
        s.setServiceChargeValue(new BigDecimal("0.10")); // 10%
        return s;
    }

    private Store mockStoreNoSC() {
        Store s = new Store();
        s.setName("ZUNO MART");
        s.setVatRegTin("456");
        s.setServiceChargeType("PERCENTAGE");
        s.setServiceChargeValue(BigDecimal.ZERO); // 0%
        return s;
    }

    private Device mockDevice() {
        Device d = new Device();
        d.setSn("001");
        d.setMinNo("MIN01");
        d.setTerminalNo("01");
        return d;
    }

    private User mockUser() {
        User u = new User();
        u.setUsername("Admin");
        return u;
    }
}