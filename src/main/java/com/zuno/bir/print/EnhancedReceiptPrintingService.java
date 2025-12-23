package com.zuno.bir.print;

import com.zuno.bir.entity.*;
import com.zuno.bir.enums.BusinessType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 增强版小票打印服务
 * <p>
 * 完全按照BIR要求的小票格式实现，支持所有业态和场景。
 */
@Service
public class EnhancedReceiptPrintingService {

    private static final int LINE_WIDTH = 40; // 小票宽度（字符数）
    private static final String LINE_SEPARATOR = "----------------------------------------\n";
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat QTY_FMT = new DecimalFormat("#0");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat DATE_ISSUE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");

    /**
     * 生成销售发票内容
     *
     * @param order     销售订单
     * @param store     店铺信息
     * @param device    设备信息
     * @param user      收银员信息
     * @param isReprint 是否重打
     * @param copyType  副本类型（CASHIER_COPY 或 CUSTOMER_COPY），有政府折扣时需要
     * @return 小票内容
     */
    public String generateReceipt(SalesOrder order, Store store, Device device, User user,
                                  boolean isReprint, String copyType) {
        StringBuilder sb = new StringBuilder();

        // 1. Header（居中）
        buildHeader(sb, store, device);

        // 2. 单据头部（普通交易：SI；逆向交易：VOID#/RETURN# + SI#）
        if(isVoidOrReturn(order)) {
            buildReverseHeader(sb, order);
        } else {
            buildSiAndDateTime(sb, order);
        }

        // 3. Table Detail（餐厅&奶茶特有）
        if(isDiningOrFastFood(order.getBusinessType())) {
            buildTableDetail(sb, order, device, user);
        }

        // 4. 标题区域（普通单：SALES INVOICE；逆向单：VOID/RETURN TRANSACTION）
        buildSalesInvoiceTitle(sb, isReprint, copyType, order);

        // 5. Product Detail
        buildProductDetail(sb, order.getItems());

        // 6. Financial Footer 金额的计算
        buildFinancialFooter(sb, order, store);

        // 7. Payment Detail 支付方式的计算
        buildPaymentDetail(sb, order.getPayments());

        // 8. Summary（商品行数、总数量）
        buildSummary(sb, order.getItems());

        // 9. VAT Analysis 税务的计算
        buildVatAnalysis(sb, order);

        // 10. Customer Info
        // 有政府折扣：按折扣类型打印 SC/PWD/NAAC/SP/MOV/DIPLOMATIC 专用区块
        // 无政府折扣：打印通用 Customer Name / Address / TIN
        buildCustomerInfo(sb, order);

        // 11. Retail Discount Quota（零售业态MGS/VBN折扣） 零售业态的折扣计算
        if(isRetailWithDiscount(order)) {
            buildDiscountQuota(sb, order);
        }

        // 12. Footer（认证信息） 认证信息的计算
        buildFooter(sb, device, store);

        // 13. Remarks 备注的计算
        if(order.getRemarks() != null && !order.getRemarks().trim().isEmpty()) {
            buildRemarks(sb, order.getRemarks());
        }

        // 14. 商家自定义信息 商家自定义信息的计算
        if(store.getCustomMessage1() != null && !store.getCustomMessage1().trim().isEmpty()) {
            sb.append(store.getCustomMessage1()).append("\n");
            sb.append(LINE_SEPARATOR);
        }

        // 15. 软件供应商自定义信息 软件供应商自定义信息的计算
        if(store.getCustomMessage2() != null && !store.getCustomMessage2().trim().isEmpty()) {
            sb.append(store.getCustomMessage2()).append("\n");
            sb.append(LINE_SEPARATOR);
        }

        // 16. 固定提示 固定提示的计算
        sb.append(alignCenter("THIS SERVES AS YOUR SALES INVOICE")).append("\n");

        return sb.toString();
    }

    // ==================== 构建方法 ====================

    /**
     * Header：门店信息（居中）
     */
    private void buildHeader(StringBuilder sb, Store store, Device device) {
        if(store.getName() != null) {
            sb.append(alignCenter(store.getName())).append("\n");
        }
        if(store.getCompanyName() != null) {
            sb.append(alignCenter(store.getCompanyName())).append("\n");
        }
        if(store.getCompanyAddress() != null) {
            sb.append(alignCenter(store.getCompanyAddress())).append("\n");
        }
        if(store.getVatRegTin() != null) {
            sb.append(alignCenter("VAT-REG TIN " + formatTin(store.getVatRegTin()))).append("\n");
        }
        if(device != null && device.getSn() != null) {
            sb.append(alignCenter("SN " + device.getSn())).append("\n");
        }
        if(device != null && device.getMinNo() != null) {
            sb.append(alignCenter("MIN# " + device.getMinNo())).append("\n");
        }
        sb.append(LINE_SEPARATOR);
    }

    /**
     * SI号和日期时间
     */
    private void buildSiAndDateTime(StringBuilder sb, SalesOrder order) {
        String siNumber = order.getSiNumber() != null ? order.getSiNumber() : "000000000";
        sb.append("SI ").append(String.format("%09d", parseSiNumber(siNumber))).append("\n");

        Date orderDate = order.getOrderDate() != null ? order.getOrderDate() : new Date();
        sb.append("Date&Time: ").append(DATE_FORMAT.format(orderDate))
                .append(" ").append(TIME_FORMAT.format(orderDate)).append("\n");
        sb.append(LINE_SEPARATOR);
    }

    /**
     * Table Detail（餐厅&奶茶特有，左对齐）
     */
    private void buildTableDetail(StringBuilder sb, SalesOrder order, Device device, User user) {
        if(order.getTableName() != null && !order.getTableName().trim().isEmpty()) {
            sb.append("Table Name: ").append(order.getTableName()).append("\n");
        }
        if(order.getBillingNo() != null) {
            sb.append("Billing#: ").append(order.getBillingNo()).append("\n");
        }
        if(order.getTrxType() != null && !order.getTrxType().trim().isEmpty()) {
            sb.append("Trx Type: ").append(order.getTrxType()).append("\n");
        }
        if(order.getPax() != null && order.getPax() > 0) {
            sb.append("Pax: ").append(order.getPax()).append("\n");
        }
        if(user != null && user.getFullName() != null) {
            sb.append("Cashier: ").append(user.getFullName()).append("\n");
        }
        if(device != null && device.getTerminalNo() != null) {
            sb.append("TERMINAL#: ").append(device.getTerminalNo()).append("\n");
        }
        sb.append(LINE_SEPARATOR);
    }

    /**
     * 标题区域：
     * - 普通交易：SALES INVOICE
     * - 退货：RETURN TRANSACTION
     * - 作废：VOID TRANSACTION
     */
    private void buildSalesInvoiceTitle(StringBuilder sb, boolean isReprint, String copyType, SalesOrder order) {
        String trxType = order.getTrxType() != null ? order.getTrxType().trim().toUpperCase() : "";
        if("RETURN".equals(trxType)) {
            sb.append(alignCenter("RETURN TRANSACTION")).append("\n");
        } else if("VOID".equals(trxType)) {
            sb.append(alignCenter("VOID TRANSACTION")).append("\n");
        } else {
            sb.append(alignCenter("SALES INVOICE")).append("\n");
        }

        if(isReprint) {
            sb.append(alignCenter("REPRINT")).append("\n");
        }
        if(hasGovernmentDiscount(order)) {
            if("CASHIER_COPY".equals(copyType)) {
                sb.append(alignCenter("Cashier Copy")).append("\n");
            } else if("CUSTOMER_COPY".equals(copyType)) {
                sb.append(alignCenter("Customer Copy")).append("\n");
            }
        }
        sb.append(LINE_SEPARATOR);
    }

    /**
     * 逆向单据头部：VOID#/RETURN#、Date&Time、SI#
     * 说明：
     * - 当前版本使用订单的 SI 号作为 VOID#/RETURN# 编号
     * - 原始 SI 号如需单独存储，可在 SalesOrder 中扩展字段
     */
    private void buildReverseHeader(StringBuilder sb, SalesOrder order) {
        String siNumber = order.getSiNumber() != null ? order.getSiNumber() : "000000000";
        String trxType = order.getTrxType() != null ? order.getTrxType().trim().toUpperCase() : "";

        String docLabel = "RETURN#";
        if("VOID".equals(trxType)) {
            docLabel = "VOID#";
        }

        // 逆向单据号（当前复用 SI 号）
        sb.append(String.format("%-10s %13s\n", docLabel, String.format("%09d", parseSiNumber(siNumber))));

        // 日期时间
        Date orderDate = order.getOrderDate() != null ? order.getOrderDate() : new Date();
        sb.append("Date&Time ")
                .append(DATE_FORMAT.format(orderDate))
                .append(" ")
                .append(TIME_FORMAT.format(orderDate))
                .append("\n");

        // 原SI号（当前同上，如后续有原始SI字段可替换）
        sb.append(String.format("%-10s %13s\n", "SI#", String.format("%09d", parseSiNumber(siNumber))));
        sb.append(LINE_SEPARATOR);
    }

    /**
     * Product Detail
     */
    private void buildProductDetail(StringBuilder sb, List<SalesOrderItem> items) {
        sb.append(String.format("%-19s %3s %7s %8s\n", "Description", "Qty", "U.Price", "Amount"));

        if(items != null) {
            for(SalesOrderItem item : items) {
                String desc = item.getDescription() != null ? item.getDescription() : "Item";
                // 商品名称超长自动换行（简化处理，实际需要按字符数换行）
                if(desc.length() > 19) {
                    // 简单换行处理
                    String[] lines = wrapText(desc, 19);
                    for(String line : lines) {
                        sb.append(line).append("\n");
                    }
                } else {
                    sb.append(desc).append("\n");
                }

                // 数据行
                // 打印小票的商品 Amount 是 u.price * qty（原始金额）
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal amount = unitPrice.multiply(new BigDecimal(qty)); // 使用原始金额

                String qtyStr = QTY_FMT.format(qty);
                String priceStr = DF.format(unitPrice);
                String amtStr = DF.format(amount);

                // 添加V标记（V/E/Z）
                String taxFlag = getTaxFlag(item);

                sb.append(String.format("%19s %3s %7s %8s %s\n", "", qtyStr, priceStr, amtStr, taxFlag));
            }
        }
        sb.append(LINE_SEPARATOR);
    }

    /**
     * Financial Footer
     */
    private void buildFinancialFooter(StringBuilder sb, SalesOrder order, Store store) {
        // Gross Sales
        printLine(sb, "Gross Sales", order.getGrossSales());

        // 检查是否有整单折扣（零售业态特有，没有政府折扣时）
        boolean hasWholeOrderDiscount = hasWholeOrderDiscount(order);

        // 如果有整单折扣，整单折扣显示为 "Regular Discount"
        if(hasWholeOrderDiscount) {
            // 整单折扣（零售业态，没有政府折扣时）
            // 整单折扣叫做 Regular Discount，只显示金额，不要任何百分比
            BigDecimal wholeOrderDiscount = order.getGovDiscountTotal() != null ?
                    order.getGovDiscountTotal() : BigDecimal.ZERO;
            if(wholeOrderDiscount.compareTo(BigDecimal.ZERO) > 0) {
                printLine(sb, "Regular Discount", wholeOrderDiscount.negate());
            }
        } else {
            // 常规折扣（商家自己的折扣，0隐藏）
            printLineIfNotZero(sb, "Regular Discount", order.getRegularDiscount(), true);
        }

        // 如果有政府折扣，LESS 12% VAT、Discount、Add 12% VAT 都应该显示，即使为0
        boolean hasGovDiscount = hasGovernmentDiscount(order);

        if(hasGovDiscount) {
            // LESS 12% VAT（有政府折扣时必须显示，即使为0）
            BigDecimal lessVat = calculateTotalLessVat(order);
            if(lessVat == null) lessVat = BigDecimal.ZERO;
            printLine(sb, "LESS 12% VAT", lessVat.negate());

            // Discount（有政府折扣时必须显示）
            BigDecimal discountAmount = calculateDiscountAmount(order);
            if(discountAmount == null) discountAmount = BigDecimal.ZERO;
            // 计算折扣百分比
            BigDecimal discountPercent = calculateDiscountPercent(order);
            String label = discountPercent != null ?
                    String.format("Discount %.0f%%", discountPercent.multiply(new BigDecimal("100"))) :
                    "Discount 20%";
            printLine(sb, label, discountAmount.negate());

            // Add 12% VAT（有政府折扣时必须显示，即使为0）
            BigDecimal addVat = calculateTotalAddVat(order);
            if(addVat == null) addVat = BigDecimal.ZERO;
            printLine(sb, "Add 12% VAT", addVat);
        }

        // Service Charge（餐厅业态，0隐藏）
        if(isDining(order.getBusinessType()) && store != null) {
            BigDecimal serviceChargePercent = getServiceChargePercent(store);
            if(serviceChargePercent != null && order.getServiceCharge() != null &&
                    order.getServiceCharge().compareTo(BigDecimal.ZERO) > 0) {
                printLineIfNotZero(sb,
                        String.format("Service Charge( %.0f%%)",
                                serviceChargePercent.multiply(new BigDecimal("100"))),
                        order.getServiceCharge(), false);
            }
        }

        sb.append(LINE_SEPARATOR);

        // Amount Due（加粗）
        printLineBold(sb, "Amount Due", order.getAmountDue());
        sb.append(LINE_SEPARATOR);
    }

    /**
     * Payment Detail
     */
    private void buildPaymentDetail(StringBuilder sb, List<OrderPayment> payments) {
        if(payments == null || payments.isEmpty()) {
            return;
        }

        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal changeAmount = BigDecimal.ZERO;

        for(OrderPayment payment : payments) {
            if(payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String paymentType = payment.getType() != null ? payment.getType() : "CASH";

            if("CASH".equalsIgnoreCase(paymentType)) {
                printLineIfNotZero(sb, "CASH", payment.getAmount(), false);
                totalPaid = totalPaid.add(payment.getAmount());
                if(payment.getChangeAmount() != null) {
                    changeAmount = changeAmount.add(payment.getChangeAmount());
                }
            } else if("CREDIT_CARD".equalsIgnoreCase(paymentType) || "CREDIT CARD".equalsIgnoreCase(paymentType)) {
                printLineIfNotZero(sb, "CREDIT CARD", payment.getAmount(), false);
                totalPaid = totalPaid.add(payment.getAmount());
                if(payment.getCardNo() != null && !payment.getCardNo().trim().isEmpty()) {
                    String maskedCardNo = maskCardNumber(payment.getCardNo());
                    sb.append(" --Credit Card No. ").append(maskedCardNo).append("\n");
                }
            } else if("BALANCE".equalsIgnoreCase(paymentType)) {
                printLineIfNotZero(sb, "BALANCE", payment.getAmount(), false);
                totalPaid = totalPaid.add(payment.getAmount());
                if(payment.getBalanceBefore() != null) {
                    printLineIfNotZero(sb, "Balance Before", payment.getBalanceBefore(), false);
                }
                if(payment.getBalanceAfter() != null) {
                    printLineIfNotZero(sb, "Balance After", payment.getBalanceAfter(), false);
                }
            } else {
                // 其他支付方式
                printLineIfNotZero(sb, paymentType, payment.getAmount(), false);
                totalPaid = totalPaid.add(payment.getAmount());
            }
        }

        // CHANGE（找零，加粗，0隐藏）
        if(changeAmount.compareTo(BigDecimal.ZERO) > 0) {
            printLineBold(sb, "CHANGE", changeAmount);
        }

        sb.append(LINE_SEPARATOR);
    }

    /**
     * Summary（商品行数、总数量）
     */
    private void buildSummary(StringBuilder sb, List<SalesOrderItem> items) {
        if(items == null || items.isEmpty()) {
            return;
        }

        int itemCount = items.size();
        int totalQty = items.stream()
                .mapToInt(item->item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();

        sb.append("Number of Items ").append(itemCount).append("\n");
        sb.append("Total Qty ").append(totalQty).append("\n");
        sb.append(LINE_SEPARATOR);
    }

    /**
     * VAT Analysis
     */
    private void buildVatAnalysis(StringBuilder sb, SalesOrder order) {
        // 如果商品含税，VAT AMOUNT 和 vatable 应该有值
        // 如果商品含税但被政府折扣去税，那就是 vat exempt
        BigDecimal vatable = order.getVatableSales() != null ? order.getVatableSales() : BigDecimal.ZERO;
        BigDecimal vatAmount = order.getVatAmount() != null ? order.getVatAmount() : BigDecimal.ZERO;
        BigDecimal vatExempt = order.getVatExemptSales() != null ? order.getVatExemptSales() : BigDecimal.ZERO;
        BigDecimal zeroRated = order.getZeroRatedSales() != null ? order.getZeroRatedSales() : BigDecimal.ZERO;

        printLine(sb, "Vatable Sales", vatable);
        printLine(sb, "VAT Amount(12%)", vatAmount);
        printLine(sb, "VAT Exempt Sales", vatExempt);
        printLine(sb, "Zero-Rated Sales", zeroRated);
        sb.append(LINE_SEPARATOR);
    }

    /**
     * Customer Info（有政府折扣时显示）
     * <p>
     * 规则参考 {@link ReceiptPrintingService#buildCustomerInfo}：
     * - 每一种政府折扣类型（SC/PWD/NAAC/SP/MOV/DIPLOMATIC）打印一组独立的区块
     * - 区块包含：XXX name / XXX Address / XXX TIN / XXX Signature
     * - 如果没有政府折扣，则保持原来的通用 Customer Name/Address/TIN 结构
     */
    private void buildCustomerInfo(StringBuilder sb, SalesOrder order) {
        List<GovernmentDiscount> discounts = order.getGovDiscounts();
        boolean hasGovDiscount = discounts != null && !discounts.isEmpty();

        if(hasGovDiscount) {
            // 有政府折扣：为每个折扣人群打印单独区块
            for(GovernmentDiscount gd : discounts) {
                String type = gd.getPersonType(); // SC, PWD, NAAC, SP, MOV, DIPLOMATIC...

                // 当前版本仍然从订单上读取姓名/地址/TIN，后续可扩展到 GovernmentDiscount 自身字段
                String nameVal = order.getCustomerName() != null ? order.getCustomerName() : "";
                String addrVal = order.getCustomerAddress() != null ? order.getCustomerAddress() : "";
                String tinVal = order.getCustomerTin() != null ? order.getCustomerTin() : "";

                // 与参考图片一致：前缀为折扣类型大写 + 固定后缀
                sb.append(String.format("%-20s %s\n", getNameLabel(type), nameVal));
                sb.append(String.format("%-20s %s\n", getAddressLabel(type), addrVal));
                sb.append(String.format("%-20s %s\n", getTinLabel(type), tinVal));
                sb.append(String.format("%-20s %s\n", getSignatureLabel(type), "_________________"));
                sb.append(LINE_SEPARATOR);
            }
        } else {
            // 无政府折扣：保持原来的通用字段（如果有值才打印）
            boolean hasAny = false;
            if(order.getCustomerName() != null && !order.getCustomerName().trim().isEmpty()) {
                sb.append("Customer Name: ").append(order.getCustomerName()).append("\n");
                hasAny = true;
            }
            if(order.getCustomerAddress() != null && !order.getCustomerAddress().trim().isEmpty()) {
                sb.append("Address: ").append(order.getCustomerAddress()).append("\n");
                hasAny = true;
            }
            if(order.getCustomerTin() != null && !order.getCustomerTin().trim().isEmpty()) {
                sb.append("TIN: ").append(order.getCustomerTin()).append("\n");
                hasAny = true;
            }
            if(hasAny) {
                sb.append(LINE_SEPARATOR);
            }
        }
    }

    /**
     * Retail Discount Quota（零售业态MGS/VBN折扣）
     */
    private void buildDiscountQuota(StringBuilder sb, SalesOrder order) {
        if(!isRetailWithDiscount(order)) {
            return;
        }

        // 从服务中获取折扣配额信息
        try {
            com.zuno.bir.service.IDiscountQuotaService quotaService =
                    new com.zuno.bir.service.impl.DiscountQuotaServiceImpl();
            List<com.zuno.bir.entity.DiscountQuota> quotas = quotaService.getDiscountQuotas(order);

            if(quotas != null && !quotas.isEmpty()) {
                // 取第一个配额信息（通常一个订单只有一个折扣类型）
                com.zuno.bir.entity.DiscountQuota quota = quotas.get(0);

                if(quota.getPreviousDiscountAmount() != null) {
                    sb.append("Previous Discount Amount ").append(DF.format(quota.getPreviousDiscountAmount())).append("\n");
                }
                if(quota.getCurrentDiscountAmount() != null) {
                    sb.append("Current Discount Amount ").append(DF.format(quota.getCurrentDiscountAmount())).append("\n");
                }
                if(quota.getRemainingWeeklyQuota() != null) {
                    sb.append("Remaining Weekly Quota ").append(DF.format(quota.getRemainingWeeklyQuota())).append("\n");
                }
            }
        } catch(Exception e) {
            // 如果获取配额失败，不显示配额信息
            // 实际项目中应该记录日志
        }
    }

    /**
     * Footer（认证信息）
     */
    private void buildFooter(StringBuilder sb, Device device, Store store) {
        // Date of Issue
        if(device != null && device.getDateOfIssue() != null) {
            sb.append("Date of Issue: ").append(DATE_ISSUE_FORMAT.format(device.getDateOfIssue())).append("\n");
        }

        // PTU No
        if(device != null && device.getPtuNo() != null) {
            sb.append("PTU No. ").append(device.getPtuNo()).append("\n");
        }

        sb.append(LINE_SEPARATOR);
    }

    /**
     * Remarks
     */
    private void buildRemarks(StringBuilder sb, String remarks) {
        sb.append("Remarks: ").append(remarks).append("\n");
        sb.append(LINE_SEPARATOR);
    }

    // ==================== 辅助方法 ====================

    private String alignCenter(String text) {
        if(text == null) return "";
        int pad = (LINE_WIDTH - text.length()) / 2;
        if(pad <= 0) return text;
        return String.format("%" + pad + "s%s", "", text);
    }

    private void printLine(StringBuilder sb, String label, BigDecimal value) {
        if(value == null) value = BigDecimal.ZERO;
        sb.append(String.format("%-25s %13s\n", label, DF.format(value)));
    }

    private void printLineIfNotZero(StringBuilder sb, String label, BigDecimal value, boolean negate) {
        if(value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return; // 0隐藏
        }
        BigDecimal displayValue = negate ? value.negate() : value;
        printLine(sb, label, displayValue);
    }

    private void printLineBold(StringBuilder sb, String label, BigDecimal value) {
        // 加粗标记（实际打印时可能需要ESC/POS指令）
        if(value == null) {
            value = BigDecimal.ZERO;
        }
        sb.append(String.format("%-25s %13s\n", label + " (BOLD)", DF.format(value)));
    }

    private String getTaxFlag(SalesOrderItem item) {
        String vatType = item.getVatType() != null ? item.getVatType() : "VATABLE";
        switch (vatType.toUpperCase()) {
            case "VATABLE":
                return "(V)";
            case "VAT_EXEMPT":
                return "(E)";
            case "ZERO_RATED":
                return "(Z)";
            default:
                return "(V)";
        }
    }

    private boolean hasGovernmentDiscount(SalesOrder order) {
        // 检查是否有政府折扣
        if(order.getGovDiscounts() == null || order.getGovDiscounts().isEmpty()) {
            return false;
        }

        // 检查政府折扣列表中是否有有效的折扣类型（不是整单折扣）
        // 整单折扣时，govDiscounts应该为空，govDiscountTotal有值
        // 政府折扣时，govDiscounts不为空，且包含有效的折扣类型
        for(GovernmentDiscount gd : order.getGovDiscounts()) {
            if(gd.getPersonType() != null && !gd.getPersonType().trim().isEmpty()) {
                // 有有效的折扣类型，说明是政府折扣
                return true;
            }
        }

        return false;
    }

    private boolean isDining(String businessType) {
        return BusinessType.DINING.name().equalsIgnoreCase(businessType);
    }

    private boolean isDiningOrFastFood(String businessType) {
        return BusinessType.DINING.name().equalsIgnoreCase(businessType) ||
                BusinessType.FAST_FOOD.name().equalsIgnoreCase(businessType);
    }

    private boolean isRetailWithDiscount(SalesOrder order) {
        return BusinessType.RETAIL.name().equalsIgnoreCase(order.getBusinessType()) &&
                hasGovernmentDiscount(order);
    }

    /**
     * 检查是否有整单折扣（零售业态特有）
     * <p>
     * 整单折扣的条件：
     * 1. 零售业态
     * 2. 没有政府折扣（govDiscounts为空或为空列表）
     * 3. 有govDiscountTotal（实际存储的是整单折扣金额）
     * 4. 常规折扣为0或null（如果有常规折扣，那应该是常规折扣，不是整单折扣）
     */
    private boolean hasWholeOrderDiscount(SalesOrder order) {
        // 必须是零售业态
        if(!BusinessType.RETAIL.name().equalsIgnoreCase(order.getBusinessType())) {
            return false;
        }

        // 必须没有政府折扣
        if(hasGovernmentDiscount(order)) {
            return false;
        }

        // 必须有govDiscountTotal（整单折扣存储在这里）
        if(order.getGovDiscountTotal() == null ||
                order.getGovDiscountTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // 常规折扣应该为0（如果有常规折扣，那应该是常规折扣，不是整单折扣）
        // 但这里允许有常规折扣，因为整单折扣是在常规折扣之后计算的

        return true;
    }

    private BigDecimal calculateTotalLessVat(SalesOrder order) {
        if(order.getGovDiscounts() == null) {
            return BigDecimal.ZERO;
        }
        return order.getGovDiscounts().stream()
                .map(GovernmentDiscount::getLessVat)
                .filter(v->v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalAddVat(SalesOrder order) {
        if(order.getGovDiscounts() == null) {
            return BigDecimal.ZERO;
        }
        return order.getGovDiscounts().stream()
                .map(GovernmentDiscount::getAddVat)
                .filter(v->v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算折扣金额（不包括税减免）
     */
    private BigDecimal calculateDiscountAmount(SalesOrder order) {
        if(order.getGovDiscounts() == null || order.getGovDiscounts().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalLessVat = calculateTotalLessVat(order);
        BigDecimal totalAddVat  = calculateTotalAddVat(order);
        BigDecimal govDiscountTotal = order.getGovDiscountTotal() != null
                ? order.getGovDiscountTotal() : BigDecimal.ZERO;

        // 实际“去掉的税额” = Less VAT - Add VAT
        BigDecimal taxReduction = (totalLessVat != null ? totalLessVat : BigDecimal.ZERO)
                .subtract(totalAddVat != null ? totalAddVat : BigDecimal.ZERO);

        // 纯折扣金额 = 政府折扣总额 - 实际去掉的税额
        return govDiscountTotal.subtract(taxReduction);
    }

    private BigDecimal calculateDiscountPercent(SalesOrder order) {
        if(order.getGovDiscounts() == null || order.getGovDiscounts().isEmpty()) {
            return null;
        }

        // 优先从已应用折扣的商品行推导折扣率：
        // 通过 item.appliedDiscountType + discountTag 决定具体折扣百分比
        if(order.getItems() != null) {
            for(SalesOrderItem item : order.getItems()) {
                if(item.getAppliedDiscountType() != null) {
                    try {
                        com.zuno.bir.enums.DiscountType discType =
                                com.zuno.bir.engine.DiscountHelpers.parseDiscountType(item.getAppliedDiscountType());
                        com.zuno.bir.enums.ItemTag tag =
                                com.zuno.bir.engine.DiscountHelpers.parseItemTag(item.getDiscountTag());
                        BigDecimal rate = com.zuno.bir.engine.DiscountHelpers.getDiscountRate(discType, tag);
                        if(rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                            return rate;
                        }
                    } catch(Exception ignored) {
                    }
                }
            }
        }

        // 兜底：如果无法从商品行推导，则根据第一个政府折扣类型粗略推断
        GovernmentDiscount gd = order.getGovDiscounts().get(0);
        if(gd != null && gd.getPersonType() != null) {
            String t = gd.getPersonType().toUpperCase();
            if("SP".equals(t)) return new BigDecimal("0.10");
            if("DIPLOMATIC".equals(t)) return BigDecimal.ZERO;
            // 其余（SC/PWD/NAAC/MOV）默认 20%
            return new BigDecimal("0.20");
        }

        return null;
    }

    private BigDecimal getServiceChargePercent(Store store) {
        if(store == null) {
            return null;
        }

        // 如果服务费类型是百分比，返回百分比值
        if("PERCENTAGE".equalsIgnoreCase(store.getServiceChargeType()) &&
                store.getServiceChargeValue() != null) {
            return store.getServiceChargeValue();
        }

        return null;
    }

    private BigDecimal getOrderAmountDue(List<OrderPayment> payments) {
        if(payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .map(OrderPayment::getAmount)
                .filter(amount->amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatTin(String tin) {
        if(tin == null) return "";
        // 格式化TIN：###-###-###-#####
        return tin.replaceAll("(\\d{3})(\\d{3})(\\d{3})(\\d{5})", "$1-$2-$3-$4");
    }

    private int parseSiNumber(String siNumber) {
        try {
            // 移除SI前缀和空格
            String number = siNumber.replaceAll("[^0-9]", "");
            return Integer.parseInt(number);
        } catch(Exception e) {
            return 0;
        }
    }

    private String maskCardNumber(String cardNo) {
        if(cardNo == null || cardNo.length() < 4) {
            return "****";
        }
        // 显示最后4位
        return "*******" + cardNo.substring(cardNo.length() - 4);
    }

    // ===== 客户信息标签生成（按折扣类型动态切换） =====

    private String getNameLabel(String type) {
        if(type == null) return "Customer name";
        String prefix = type.toUpperCase();
        return prefix + " name";
    }

    private String getAddressLabel(String type) {
        if(type == null) return "Address";
        String prefix = type.toUpperCase();
        return prefix + " Address";
    }

    private String getTinLabel(String type) {
        if(type == null) return "TIN";
        String prefix = type.toUpperCase();
        return prefix + " TIN";
    }

    private String getSignatureLabel(String type) {
        if(type == null) return "Signature";
        String prefix = type.toUpperCase();
        return prefix + " Signature";
    }

    private String[] wrapText(String text, int maxWidth) {
        // 简单的文本换行
        if(text.length() <= maxWidth) {
            return new String[]{text};
        }
        // 简化处理，实际需要更智能的换行算法
        int lines = (text.length() + maxWidth - 1) / maxWidth;
        String[] result = new String[lines];
        for(int i = 0; i < lines; i++) {
            int start = i * maxWidth;
            int end = Math.min(start + maxWidth, text.length());
            result[i] = text.substring(start, end);
        }
        return result;
    }

    /**
     * 判断是否为逆向交易（VOID 或 RETURN）
     */
    private boolean isVoidOrReturn(SalesOrder order) {
        if(order == null || order.getTrxType() == null) {
            return false;
        }
        String type = order.getTrxType().trim().toUpperCase();
        return "VOID".equals(type) || "RETURN".equals(type);
    }
}

