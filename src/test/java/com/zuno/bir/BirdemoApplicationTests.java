//package com.zuno.bir;
//
//
//import com.zuno.bir.engine.DiscountEngine;
//import com.zuno.bir.entity.GovernmentDiscount;
//import com.zuno.bir.entity.SalesOrder;
//import com.zuno.bir.entity.SalesOrderItem;
//import org.junit.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
//@SpringBootTest
//public class BirdemoApplicationTests {
//    static class TestCase {
//        String name;
//        SalesOrder order;
//        BigDecimal expectedAmountDue;
//
//        public TestCase(String name, SalesOrder order, BigDecimal expectedAmountDue) {
//            this.name = name;
//            this.order = order;
//            this.expectedAmountDue = expectedAmountDue;
//        }
//    }
//
//    @Test
//    public void runAllTestCases() {
//        List<TestCase> testCases = new ArrayList<>();
//
//        // ===== 案例1: 单商品, Regular + Gov SC =====
//        SalesOrder order1 = new SalesOrder();
//        order1.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item1 = new SalesOrderItem();
//        item1.setItemId(1L);
//        item1.setDescription("Coke 600ml");
//        item1.setQuantity(2);
//        item1.setUnitPrice(new BigDecimal("56.00"));
//        item1.setVatType("VATABLE");
//        item1.setRegularDiscount(new BigDecimal("5.0"));
//        order1.setItems(Arrays.asList(item1));
//        GovernmentDiscount gd1 = new GovernmentDiscount();
//        gd1.setItemId(1L);
//        gd1.setPersonType("SC");
//        gd1.setDiscount(new BigDecimal("20"));
//        gd1.setSequenceNo(1);
//        order1.setGovDiscounts(Arrays.asList(gd1));
//        testCases.add(new TestCase("案例1", order1, new BigDecimal("105.60")));
//
//        // ===== 案例2: 多商品, Regular + Gov PWD =====
//        SalesOrder order2 = new SalesOrder();
//        order2.setServiceCharge(new BigDecimal("10.0"));
//        SalesOrderItem item2a = new SalesOrderItem();
//        item2a.setItemId(1L);
//        item2a.setDescription("Burger");
//        item2a.setQuantity(1);
//        item2a.setUnitPrice(new BigDecimal("120.0"));
//        item2a.setVatType("VATABLE");
//        item2a.setRegularDiscount(new BigDecimal("10.0"));
//
//        SalesOrderItem item2b = new SalesOrderItem();
//        item2b.setItemId(2L);
//        item2b.setDescription("Fries");
//        item2b.setQuantity(2);
//        item2b.setUnitPrice(new BigDecimal("50.0"));
//        item2b.setVatType("VATABLE");
//        item2b.setRegularDiscount(BigDecimal.ZERO);
//
//        order2.setItems(Arrays.asList(item2a, item2b));
//        GovernmentDiscount gd2 = new GovernmentDiscount();
//        gd2.setItemId(1L);
//        gd2.setPersonType("PWD");
//        gd2.setDiscount(new BigDecimal("5.0"));
//        gd2.setSequenceNo(1);
//        order2.setGovDiscounts(Arrays.asList(gd2));
//        testCases.add(new TestCase("案例2", order2, new BigDecimal("215.76")));
//
//        // ===== 案例3~10: 不同场景 =====
////        testCases.add(createTestCase3());
////        testCases.add(createTestCase4());
////        testCases.add(createTestCase5());
////        testCases.add(createTestCase6());
////        testCases.add(createTestCase7());
////        testCases.add(createTestCase8());
////        testCases.add(createTestCase9());
////        testCases.add(createTestCase10());
//
//        // ===== 运行测试 =====
//        System.out.println("=== 自动回归测试开始 ===");
//        for (TestCase tc : testCases) {
//            System.out.println("\n--- 测试: " + tc.name + " ---");
//            DiscountEngine.calculateOrder(tc.order, tc.order.getItems(), tc.order.getGovDiscounts());
//
//            BigDecimal actual = tc.order.getAmountDue();
//            System.out.println("预期金额: " + tc.expectedAmountDue + ", 实际金额: " + actual);
//
//            if (tc.expectedAmountDue.compareTo(actual) != 0) {
//                System.out.println("⚠️ 差异 detected!");
//                for (SalesOrderItem item : tc.order.getItems()) {
//                    System.out.println("商品: " + item.getDescription() + ", 最终金额: " + item.getAmount());
//                }
//            } else {
//                System.out.println("✅ 金额正确");
//            }
//        }
//        System.out.println("=== 自动回归测试结束 ===");
//    }
//
//    // 辅助方法生成案例3~10
//    private TestCase createTestCase3() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Coffee");
//        item.setQuantity(1);
//        item.setUnitPrice(new BigDecimal("80"));
//        item.setVatType("VATABLE");
//        item.setRegularDiscount(BigDecimal.ZERO);
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("NAAC");
//        gd.setDiscount(new BigDecimal("10"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例3", o, new BigDecimal("79.20"));
//    }
//
//    private TestCase createTestCase4() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Tea");
//        item.setQuantity(3);
//        item.setUnitPrice(new BigDecimal("30"));
//        item.setVatType("VATABLE");
//        item.setRegularDiscount(new BigDecimal("5"));
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("MOV");
//        gd.setDiscount(new BigDecimal("15"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例4", o, new BigDecimal("88.68"));
//    }
//
//    private TestCase createTestCase5() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Sandwich");
//        item.setQuantity(2);
//        item.setUnitPrice(new BigDecimal("50"));
//        item.setVatType("VAT_EXEMPT");
//        item.setRegularDiscount(BigDecimal.ZERO);
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("SC");
//        gd.setDiscount(new BigDecimal("20"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例5", o, new BigDecimal("80.00"));
//    }
//
//    private TestCase createTestCase6() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(new BigDecimal("5"));
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Noodles");
//        item.setQuantity(1);
//        item.setUnitPrice(new BigDecimal("100"));
//        item.setVatType("VATABLE");
//        item.setRegularDiscount(new BigDecimal("10"));
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("PWD");
//        gd.setDiscount(new BigDecimal("15"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例6", o, new BigDecimal("97.44"));
//    }
//
//    private TestCase createTestCase7() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Juice");
//        item.setQuantity(2);
//        item.setUnitPrice(new BigDecimal("40"));
//        item.setVatType("ZERO_RATED");
//        item.setRegularDiscount(BigDecimal.ZERO);
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("DIPLOMATIC");
//        gd.setDiscount(new BigDecimal("10"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例7", o, new BigDecimal("70.00"));
//    }
//
//    private TestCase createTestCase8() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item1 = new SalesOrderItem();
//        item1.setItemId(1L);
//        item1.setDescription("Pizza");
//        item1.setQuantity(1);
//        item1.setUnitPrice(new BigDecimal("150"));
//        item1.setVatType("VATABLE");
//        item1.setRegularDiscount(BigDecimal.ZERO);
//        SalesOrderItem item2 = new SalesOrderItem();
//        item2.setItemId(2L);
//        item2.setDescription("Cola");
//        item2.setQuantity(2);
//        item2.setUnitPrice(new BigDecimal("30"));
//        item2.setVatType("VATABLE");
//        item2.setRegularDiscount(new BigDecimal("5"));
//        o.setItems(Arrays.asList(item1, item2));
//        GovernmentDiscount gd1 = new GovernmentDiscount();
//        gd1.setItemId(1L);
//        gd1.setPersonType("SC");
//        gd1.setDiscount(new BigDecimal("20"));
//        gd1.setSequenceNo(1);
//        GovernmentDiscount gd2 = new GovernmentDiscount();
//        gd2.setItemId(2L);
//        gd2.setPersonType("PWD");
//        gd2.setDiscount(new BigDecimal("5"));
//        gd2.setSequenceNo(2);
//        o.setGovDiscounts(Arrays.asList(gd1, gd2));
//        return new TestCase("案例8", o, new BigDecimal("177.12"));
//    }
//
//    private TestCase createTestCase9() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(BigDecimal.ZERO);
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Milk");
//        item.setQuantity(3);
//        item.setUnitPrice(new BigDecimal("25"));
//        item.setVatType("VATABLE");
//        item.setRegularDiscount(new BigDecimal("5"));
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("NAAC");
//        gd.setDiscount(new BigDecimal("10"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例9", o, new BigDecimal("72.36"));
//    }
//
//    private TestCase createTestCase10() {
//        SalesOrder o = new SalesOrder();
//        o.setServiceCharge(new BigDecimal("3"));
//        SalesOrderItem item = new SalesOrderItem();
//        item.setItemId(1L);
//        item.setDescription("Cake");
//        item.setQuantity(1);
//        item.setUnitPrice(new BigDecimal("80"));
//        item.setVatType("VAT_EXEMPT");
//        item.setRegularDiscount(BigDecimal.ZERO);
//        o.setItems(Arrays.asList(item));
//        GovernmentDiscount gd = new GovernmentDiscount();
//        gd.setItemId(1L);
//        gd.setPersonType("SC");
//        gd.setDiscount(new BigDecimal("15"));
//        gd.setSequenceNo(1);
//        o.setGovDiscounts(Arrays.asList(gd));
//        return new TestCase("案例10", o, new BigDecimal("68.00"));
//    }
//}
//
