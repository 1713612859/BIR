package com.zuno.bir;

import com.zuno.bir.engine.core.CalculationContext;
import com.zuno.bir.engine.core.CalculationResult;
import com.zuno.bir.engine.core.impl.RetailOrderCalculator;
import com.zuno.bir.entity.*;
import com.zuno.bir.enums.BusinessType;
import com.zuno.bir.enums.DiscountType;
import com.zuno.bir.print.EnhancedReceiptPrintingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 零售业态订单计算器测试
 */
public class RetailOrderCalculatorTest {

    private Store store;
    private Device device;
    private User user;

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

        device = new Device();
        device.setDeviceId(1L);
        device.setStoreId(1L);
        device.setSn("SN123456");
        device.setMinNo("1234567879");
        device.setTerminalNo("123456789");
        device.setDateOfIssue(new java.util.Date());
        device.setPtuNo("123456789");
    }

    private RetailOrderCalculator calculator = new RetailOrderCalculator();

    @Test
    @DisplayName("零售业态 - 无折扣订单")
    public void testRetailWithoutDiscount() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 1);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(null)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        // 确保订单和商品项已更新
        order.setItems(items);
        order.setGrossSales(result.getTotalGrossSales());
        order.setVatableSales(result.getTotalVatableSales());
        order.setVatAmount(result.getTotalVatAmount());
        order.setGovDiscountTotal(result.getTotalGovernmentDiscount());
        order.setAmountDue(result.getTotalVatableSales().add(result.getTotalVatAmount())
                .add(result.getTotalVatExemptSales()).add(result.getTotalZeroRatedSales()));

        // 验证结果
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertEquals(new BigDecimal("100.00"), result.getTotalVatableSales());
        assertEquals(new BigDecimal("12.00"), result.getTotalVatAmount());
        assertEquals(BigDecimal.ZERO, result.getTotalGovernmentDiscount());

        // 验证商品金额已设置
        assertNotNull(items.get(0).getAmount());
        assertTrue(items.get(0).getAmount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, null);
        System.out.println("=== 零售业态 - 无折扣订单 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - 单个政府折扣（SC + MGS商品）")
    public void testRetailWithSingleGovernmentDiscount() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        // 确保订单和商品项已更新
        order.setItems(items);
        order.setGovDiscounts(discounts);
        order.setGrossSales(result.getTotalGrossSales());
        order.setVatableSales(result.getTotalVatableSales());
        order.setVatAmount(result.getTotalVatAmount());
        order.setVatExemptSales(result.getTotalVatExemptSales());
        order.setGovDiscountTotal(result.getTotalGovernmentDiscount());
        order.setAmountDue(result.getTotalVatableSales().add(result.getTotalVatAmount())
                .add(result.getTotalVatExemptSales()).add(result.getTotalZeroRatedSales()));

        // 验证结果：MGS商品20%折扣，免税
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        // 验证商品金额已设置
        assertNotNull(items.get(0).getAmount());

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - SC + MGS商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - 整单金额折扣")
    public void testRetailWithWholeOrderAmountDiscount() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 2);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(null)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .wholeOrderDiscountAmount(new BigDecimal("20.00"))
                .build();

        CalculationResult result = calculator.calculate(context);

        // 验证结果：Gross - 常规折扣 - 整单折扣，再计算税
        assertEquals(new BigDecimal("224.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        System.out.println(service.generateReceipt(order, store, device, user, false, null));
    }

    @Test
    @DisplayName("零售业态 - 整单百分比折扣")
    public void testRetailWithWholeOrderPercentDiscount() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 1);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(null)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .wholeOrderDiscountPercent(new BigDecimal("0.10")) // 10%折扣
                .build();

        CalculationResult result = calculator.calculate(context);

        // 验证结果
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        System.out.println(service.generateReceipt(order, store, device, user, false, null));
    }

    @Test
    @DisplayName("零售业态 - VBN商品折扣（含税）")
    public void testRetailWithVBNDiscount() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "VBN", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        // 验证结果：VBN商品5%折扣，含税
        assertTrue(result.getTotalVatAmount().compareTo(BigDecimal.ZERO) > 0); // 含税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        System.out.println(service.generateReceipt(order, store, device, user, false, null));
    }

    @Test
    @DisplayName("零售业态 - 混合商品（MGS + VBN）")
    public void testRetailWithMixedItems() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = new ArrayList<>();

        // MGS商品
        SalesOrderItem mgsItem = createItem("MGS Item", "VATABLE", "MGS", new BigDecimal("112.00"), 1);
        items.add(mgsItem);

        // VBN商品
        SalesOrderItem vbnItem = createItem("VBN Item", "VATABLE", "VBN", new BigDecimal("112.00"), 1);
        items.add(vbnItem);

        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        // 确保订单和商品项已更新
        order.setItems(items);
        order.setGovDiscounts(discounts);
        order.setGrossSales(result.getTotalGrossSales());
        order.setRegularDiscount(result.getTotalRegularDiscount());
        order.setVatableSales(result.getTotalVatableSales());
        order.setVatAmount(result.getTotalVatAmount());
        order.setVatExemptSales(result.getTotalVatExemptSales());
        order.setZeroRatedSales(result.getTotalZeroRatedSales());
        order.setGovDiscountTotal(result.getTotalGovernmentDiscount());
        order.setAmountDue(result.getTotalVatableSales().add(result.getTotalVatAmount())
                .add(result.getTotalVatExemptSales()).add(result.getTotalZeroRatedSales()));
        order.setOrderDate(new java.util.Date());

        // 验证结果：有VBN商品，整单含税
        assertTrue(result.getTotalVatAmount().compareTo(BigDecimal.ZERO) > 0);

        // 验证商品金额已设置
        assertNotNull(items.get(0).getAmount());
        assertNotNull(items.get(1).getAmount());

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - 混合商品（MGS + VBN）===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - PWD + MGS商品（20%折扣，免税）")
    public void testRetailPWDWithMGS() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.PWD, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：MGS商品20%折扣，免税
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - PWD + MGS商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - PWD + VBN商品（5%折扣，含税）")
    public void testRetailPWDWithVBN() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "VBN", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.PWD, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：VBN商品5%折扣，含税
        assertTrue(result.getTotalVatAmount().compareTo(BigDecimal.ZERO) > 0); // 含税
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - PWD + VBN商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - SP + SP商品（10%折扣，免税）")
    public void testRetailSPWithSPItem() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "SP", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SP, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：SP商品10%折扣，免税
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - SP + SP商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - NAAC + NAAC商品（20%折扣）")
    public void testRetailNAACWithNAACItem() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NAAC", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.NAAC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：NAAC商品20%折扣
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - NAAC + NAAC商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - NAAC + MGS商品（20%折扣，免税）")
    public void testRetailNAACWithMGS() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.NAAC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：MGS商品20%折扣，免税
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - NAAC + MGS商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - MOV + MOV商品（20%折扣）")
    public void testRetailMOVWithMOVItem() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MOV", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.MOV, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：MOV商品20%折扣
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - MOV + MOV商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - MOV + MGS商品（20%折扣，免税）")
    public void testRetailMOVWithMGS() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "MGS", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.MOV, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：MGS商品20%折扣，免税
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - MOV + MGS商品折扣 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - DIPLOMATIC + 含税商品（免税，0%折扣）")
    public void testRetailDiplomaticWithVatableItem() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VATABLE", "NONE", new BigDecimal("112.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.DIPLOMATIC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：DIPLOMATIC免税，0%折扣
        assertEquals(new BigDecimal("112.00"), result.getTotalGrossSales());
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税
        // DIPLOMATIC的折扣金额应该等于税额（免税），但折扣率是0%
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0); // 免税金额

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - DIPLOMATIC + 含税商品（免税）===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - DIPLOMATIC + 不含税商品（无折扣）")
    public void testRetailDiplomaticWithExemptItem() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = createItems("VAT_EXEMPT", "NONE", new BigDecimal("100.00"), 1);
        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.DIPLOMATIC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：DIPLOMATIC对不含税商品无折扣
        assertEquals(new BigDecimal("100.00"), result.getTotalGrossSales());
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount());
        assertEquals(new BigDecimal("0.00"), result.getTotalGovernmentDiscount()); // 不含税商品无折扣

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - DIPLOMATIC + 不含税商品 ===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - SC + 混合商品（MGS + VBN + SP + NAAC）")
    public void testRetailSCWithMixedItemTags() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = new ArrayList<>();

        // MGS商品
        items.add(createItem("MGS Item", "VATABLE", "MGS", new BigDecimal("112.00"), 1));
        // VBN商品
        items.add(createItem("VBN Item", "VATABLE", "VBN", new BigDecimal("112.00"), 1));
        // SP商品（SC不支持SP商品折扣）
        items.add(createItem("SP Item", "VATABLE", "SP", new BigDecimal("112.00"), 1));
        // NAAC商品（SC不支持NAAC商品折扣）
        items.add(createItem("NAAC Item", "VATABLE", "NAAC", new BigDecimal("112.00"), 1));

        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.SC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：SC只对MGS和VBN商品有折扣，SP和NAAC商品无折扣
        assertEquals(new BigDecimal("448.00"), result.getTotalGrossSales()); // 4个商品 * 112
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        // 有VBN商品，所以有税
        assertTrue(result.getTotalVatAmount().compareTo(BigDecimal.ZERO) > 0);

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - SC + 混合商品（MGS + VBN + SP + NAAC）===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - NAAC + 混合商品（MGS + NAAC）")
    public void testRetailNAACWithMixedItems() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = new ArrayList<>();

        // MGS商品
        items.add(createItem("MGS Item", "VATABLE", "MGS", new BigDecimal("112.00"), 1));
        // NAAC商品
        items.add(createItem("NAAC Item", "VATABLE", "NAAC", new BigDecimal("112.00"), 1));

        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.NAAC, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(null)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：NAAC对MGS和NAAC商品都有20%折扣，免税
        assertEquals(new BigDecimal("224.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - NAAC + 混合商品（MGS + NAAC）===");
        System.out.println(receipt);
    }

    @Test
    @DisplayName("零售业态 - MOV + 混合商品（MGS + MOV）")
    public void testRetailMOVWithMixedItems() {
        SalesOrder order = createRetailOrder();
        List<SalesOrderItem> items = new ArrayList<>();

        // MGS商品
        items.add(createItem("MGS Item", "VATABLE", "MGS", new BigDecimal("112.00"), 1));
        // MOV商品
        items.add(createItem("MOV Item", "VATABLE", "MOV", new BigDecimal("112.00"), 1));

        List<GovernmentDiscount> discounts = createGovernmentDiscounts(DiscountType.MOV, null);

        CalculationContext context = CalculationContext.builder()
                .order(order)
                .items(items)
                .governmentDiscounts(discounts)
                .store(store)
                .businessType(BusinessType.RETAIL)
                .build();

        CalculationResult result = calculator.calculate(context);

        setupOrderForPrinting(order, items, discounts, result);

        // 验证结果：MOV对MGS和MOV商品都有20%折扣，免税
        assertEquals(new BigDecimal("224.00"), result.getTotalGrossSales());
        assertTrue(result.getTotalGovernmentDiscount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("0.00"), result.getTotalVatAmount()); // 免税

        EnhancedReceiptPrintingService service = new EnhancedReceiptPrintingService();
        String receipt = service.generateReceipt(order, store, device, user, false, "CASHIER_COPY");
        System.out.println("=== 零售业态 - MOV + 混合商品（MGS + MOV）===");
        System.out.println(receipt);
    }

    // ==================== 辅助方法 ====================

    private SalesOrder createRetailOrder() {
        SalesOrder order = new SalesOrder();
        order.setBusinessType(BusinessType.RETAIL.name());
        order.setSiNumber("000000001");
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

    /**
     * 设置订单数据用于打印
     */
    private void setupOrderForPrinting(SalesOrder order, List<SalesOrderItem> items,
                                       List<GovernmentDiscount> discounts, CalculationResult result) {
        order.setItems(items);
        order.setGovDiscounts(discounts);
        order.setGrossSales(result.getTotalGrossSales());
        order.setRegularDiscount(result.getTotalRegularDiscount());
        order.setVatableSales(result.getTotalVatableSales());
        order.setVatAmount(result.getTotalVatAmount());
        order.setVatExemptSales(result.getTotalVatExemptSales());
        order.setZeroRatedSales(result.getTotalZeroRatedSales());
        order.setGovDiscountTotal(result.getTotalGovernmentDiscount());
        order.setAmountDue(result.getTotalVatableSales().add(result.getTotalVatAmount())
                .add(result.getTotalVatExemptSales()).add(result.getTotalZeroRatedSales()));
        order.setOrderDate(new java.util.Date());
    }
}

