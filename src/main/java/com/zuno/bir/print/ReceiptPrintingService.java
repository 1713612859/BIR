package com.zuno.bir.print;

import com.zuno.bir.entity.GovernmentDiscount;
import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.entity.Store;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 销售发票打印服务 (Sales Invoice Only)
 * 专注于生成符合 BIR 合规要求的标准销售发票。
 */
@Service
public class ReceiptPrintingService {

    // 打印机通常每行 40-48 字符，这里按 40 字符布局
    private static final String LINE_SEPARATOR = "----------------------------------------\n";
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat QTY_FMT = new DecimalFormat("#0");

    /**
     * 生成销售发票内容
     *
     * @param order 销售订单实体 (包含 items 和 govDiscounts)
     * @param store 店铺信息实体
     * @return 用于发送给打印机的字符串
     */
    public String generateSalesInvoice(SalesOrder order, Store store) {
        StringBuilder sb = new StringBuilder();

        // 1. Header (店铺抬头)
        buildHeader(sb, store);

        // 2. Title (大标题) - 建议打印机指令设置加粗加大
        sb.append(alignCenter("SALES INVOICE")).append("\n");
        sb.append(LINE_SEPARATOR);

        // 3. Transaction Details (单据信息)
        buildTransactionDetails(sb, order);
        sb.append(LINE_SEPARATOR);

        // 4. Product Details (商品明细)
        buildItems(sb, order.getItems());
        sb.append(LINE_SEPARATOR);

        // 5. Financials (金额汇总 - 核心计算)
        buildFinancialFooter(sb, order);

        // 6. VAT Analysis (税务分析)
        buildVatAnalysis(sb, order);
        sb.append(LINE_SEPARATOR);

        // 7. Customer Information (客户信息 - 发票核心)
        buildCustomerInfo(sb, order);
        sb.append(LINE_SEPARATOR);

        // 8. Footer (签名栏与合规信息)
        buildInvoiceFooter(sb, store);

        return sb.toString();
    }

    // =========================================================
    // 模块构建方法
    // =========================================================

    private void buildHeader(StringBuilder sb, Store store) {
        sb.append(alignCenter(store.getName())).append("\n");
        if(store.getCompanyName() != null) {
            sb.append(alignCenter(store.getCompanyName())).append("\n");
        }
        sb.append(alignCenter(store.getAddress())).append("\n");
        sb.append(alignCenter("VAT-REG TIN " + store.getVatRegTin())).append("\n");
        // SN 和 MIN 通常从 Device 表获取，这里模拟
        sb.append(alignCenter("SN: DEVICE-001")).append("\n");
        sb.append(alignCenter("MIN: MIN-123456789")).append("\n");
        sb.append(LINE_SEPARATOR);
    }

    private void buildTransactionDetails(StringBuilder sb, SalesOrder order) {
        sb.append("SI No:   ").append(order.getSiNumber() != null ? order.getSiNumber() : "N/A").append("\n");
        sb.append("Date:    ").append(new SimpleDateFormat("MM/dd/yyyy").format(new Date())).append("\n");
        sb.append("Time:    ").append(new SimpleDateFormat("HH:mm:ss").format(new Date())).append("\n");
        // 假设 cashierId 转为了名字，实际需查表
        sb.append("Cashier: ").append(order.getCashierId() != null ? order.getCashierId() : "1001").append("\n");
        sb.append("Terminal#: ").append(order.getDeviceId() != null ? order.getDeviceId() : "1").append("\n");

        // 如果是餐饮，打印桌号
        if("DINING".equalsIgnoreCase(order.getBusinessType()) && order.getTableName() != null) {
            sb.append("Table:   ").append(order.getTableName()).append("\n");
            sb.append("Pax:     ").append(order.getPax()).append("\n");
        }
    }
// --- Helpers: 动态生成标签 --

    private String getAddressLabel(String type) {
        if(type == null) return "Address";
        return type.toUpperCase() + " Address";
    }

    private String getTinLabel(String type) {
        if(type == null) return "TIN";
        // 图片中显示的是 "[Type] TIN"，有些是 ID，这里统一适配
        return type.toUpperCase() + " TIN";
    }

    private String getSignatureLabel(String type) {
        if(type == null) return "Signature";
        return type.toUpperCase() + " Signature";
    }

    /**
     * 构建客户信息模块 (符合 BIR 多折扣展示要求)
     * 逻辑:
     * 1. 遍历所有政府折扣，为每一个折扣对象生成独立的 "姓名/地址/TIN/签字" 区块。
     * 2. 如果没有政府折扣，则显示标准的普通客户信息。
     */
    private void buildCustomerInfo(StringBuilder sb, SalesOrder order) {
        List<GovernmentDiscount> discounts = order.getGovDiscounts();

        // 检查是否有有效的政府折扣 (排除空列表)
        boolean hasGovDiscount = discounts != null && !discounts.isEmpty();

        if(hasGovDiscount) {
            // ============================================================
            // 场景 A: 存在政府折扣 - 遍历显示每一个折扣持有人的信息
            // ============================================================

            for(GovernmentDiscount gd : discounts) {
                // 通常只显示全局卡(Item ID为空)的信息，或者如果您的一卡一单逻辑是绑在 Item 上的，这里需要根据业务调整。
                // 按照之前的逻辑，这里假设 govDiscounts 列表里的每一个对象代表一张卡/一个人。

                String type = gd.getPersonType(); // SC, PWD, NAAC...

                // 1. 获取值 (优先从 discount 的 extraInfo 取，如果没有则取 Order 的通用信息)
                // 在多卡场景下，Order 上的 CustomerName 可能只是其中一个人的，
                // 实际项目中建议将每个人的信息存在 GovernmentDiscount.extraInfo JSON中解析。
                // 这里为了演示格式，暂时复用 Order 字段。
                String nameVal = order.getCustomerName() != null ? order.getCustomerName() : "";
                String addrVal = order.getCustomerAddress() != null ? order.getCustomerAddress() : "";
                String idVal = order.getCustomerTin() != null ? order.getCustomerTin() : "";

                // 2. 打印区块
                // 格式参考图片: Label + Value

                // Name
                sb.append(String.format("%-14s : %s\n", getNameLabel(type), nameVal));

                // Address
                sb.append(String.format("%-14s : %s\n", getAddressLabel(type), addrVal));

                // TIN / ID
                sb.append(String.format("%-14s : %s\n", getTinLabel(type), idVal));

                // Signature (留空线)
                sb.append(String.format("%-14s : %s\n", getSignatureLabel(type), "___________"));

                // 如果还有下一个，加个小分隔符，或者直接空一行
                sb.append("\n");
            }

        } else {
            // ============================================================
            // 场景 B: 普通客户 (无政府折扣)
            // ============================================================
            String name = order.getCustomerName() != null ? order.getCustomerName() : "";
            sb.append(String.format("%-14s : %s\n", "Customer Name", name));

            String address = order.getCustomerAddress() != null ? order.getCustomerAddress() : "";
            sb.append(String.format("%-14s : %s\n", "Address", address));

            String tin = order.getCustomerTin() != null ? order.getCustomerTin() : "";
            sb.append(String.format("%-14s : %s\n", "TIN", tin));

            // 添加普通客户的签名行
            sb.append(String.format("%-14s : %s\n", "Signature", "___________"));
        }
    }

    // --- 辅助方法: 获取 姓名 标签 ---
    private String getNameLabel(String type) {
        if(type == null) return "Name";
        switch (type.toUpperCase()) {
            case "SC":
                return "SC Name";
            case "PWD":
                return "PWD Name";
            case "NAAC":
                return "NAAC Name";
            case "MOV":
                return "MOV Name";
            case "SP":
                return "SP Name";
            case "DIPLOMATIC":
                return "Diplomat";
            default:
                return "Name";
        }
    }

    private void buildItems(StringBuilder sb, List<SalesOrderItem> items) {
        // 表头: Desc, Qty, Price, Amount (Total Gross)
        sb.append(String.format("%-19s %3s %7s %8s\n", "Description", "Qty", "Price", "Amount"));

        if(items != null) {
            for(SalesOrderItem item : items) {
                // 1. 商品名称行 (包含折扣标签)
                String desc = item.getDescription();
                if(item.getDiscountTag() != null && !"NONE".equalsIgnoreCase(item.getDiscountTag())) {
                    desc += " [" + item.getDiscountTag() + "]";
                }
                sb.append(desc).append("\n");

                // 2. 数据行
                // Qty
                String qtyStr = QTY_FMT.format(item.getQuantity());
                // Unit Price (含税单价)
                String priceStr = DF.format(item.getUnitPrice());

                // Line Amount (Gross) = Qty * UnitPrice
                // 注意：BIR发票明细通常显示 Gross Amount，折扣在底部扣除
                BigDecimal lineGross = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
                String amtStr = DF.format(lineGross);

                // 税务标记 (V/E/Z)
                String taxFlag = getTaxFlag(item);

                // 格式化输出: 空格缩进 + 数据
                sb.append(String.format("%19s %3s %7s %8s %s\n", "", qtyStr, priceStr, amtStr, taxFlag));
            }
        }
    }

    private void buildFinancialFooter(StringBuilder sb, SalesOrder order) {
        // 1. Gross Sales (总销售额)
        printLine(sb, "Gross Sales", order.getGrossSales());

        // 2. Less: Promo Discount (常规促销)
        if(order.getRegularDiscount() != null && order.getRegularDiscount().compareTo(BigDecimal.ZERO) != 0) {
            printLine(sb, "Regular Discount", order.getRegularDiscount().negate());
        }

        // 3. 政府折扣 (现场汇总计算，确保准确)
        List<GovernmentDiscount> govDiscounts = order.getGovDiscounts();
        if(govDiscounts != null && !govDiscounts.isEmpty()) {
            BigDecimal totalLessVat = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            BigDecimal totalAddVat = BigDecimal.ZERO;

            for(GovernmentDiscount gd : govDiscounts) {
                if(gd.getLessVat() != null) totalLessVat = totalLessVat.add(gd.getLessVat());
                if(gd.getDiscount() != null) totalDiscount = totalDiscount.add(gd.getDiscount());
                if(gd.getAddVat() != null) totalAddVat = totalAddVat.add(gd.getAddVat());
            }

            // 只要有去税或折扣，就打印
            if(totalLessVat.compareTo(BigDecimal.ZERO) != 0 || totalDiscount.compareTo(BigDecimal.ZERO) != 0) {
                printLine(sb, "Less 12% VAT", totalLessVat.negate());
                printLine(sb, "Discount", totalDiscount.negate());
                printLine(sb, "Add 12% VAT", totalAddVat.negate());
            }
        }

        // 4. Service Charge (服务费)
        if(order.getServiceCharge() != null && order.getServiceCharge().compareTo(BigDecimal.ZERO) != 0) {
            printLine(sb, "Service Charge ", order.getServiceCharge());
        }

        // 5. Local Tax (如果有)
        if(order.getLocalTax() != null && order.getLocalTax().compareTo(BigDecimal.ZERO) != 0) {
            printLine(sb, "Local Tax", order.getLocalTax());
        }

        sb.append(LINE_SEPARATOR);

        // 6. AMOUNT DUE (大字体)
        // 建议打印指令: 加大
        sb.append(String.format("%-20s %15s\n", "AMOUNT DUE", DF.format(order.getAmountDue())));
        sb.append(LINE_SEPARATOR);
    }

    private void buildVatAnalysis(StringBuilder sb, SalesOrder order) {
        sb.append("VAT Analysis:\n");
        printLine(sb, "Vatable Sales", order.getVatableSales());
        printLine(sb, "VAT Amount (12%)", order.getVatAmount());
        printLine(sb, "VAT Exempt Sales", order.getVatExemptSales());
        printLine(sb, "Zero Rated Sales", order.getZeroRatedSales());
    }

    private void buildInvoiceFooter(StringBuilder sb, Store store) {
        sb.append(alignCenter("THIS SERVES AS YOUR SALES INVOICE")).append("\n");
        sb.append(LINE_SEPARATOR);
        // 模拟 BIR 许可信息
        sb.append("Accreditation No: 123-456-789-000").append("\n");
        sb.append("Date Issued:      01/01/2024").append("\n");
        sb.append("Valid Until:      07/31/2029").append("\n");
        sb.append("PTU No:           FP122024-001").append("\n");
        sb.append("Date Issued:      01/01/2024").append("\n");

        sb.append(LINE_SEPARATOR);
        sb.append(alignCenter("Thank you come again!")).append("\n");
        sb.append(alignCenter("Powered by Zuno POS")).append("\n");
    }

    // =========================================================
    // 工具方法
    // =========================================================

    private String getTaxFlag(SalesOrderItem item) {
        if(item.getVatType() == null) {
            return "(V)";
        }
        if(item.getVatType().isEmpty()) {
            return "(V)";
        }
        switch (item.getVatType().toUpperCase()) {
            case "VATABLE":
                return "(V)";
            case "ZERO_RATED":
                return "(Z)";
            case "VAT_EXEMPT":
                return "(E)";
            default:
                return "(V)"; // 默认为应税
        }
    }

    private void printLine(StringBuilder sb, String label, BigDecimal value) {
        if(value == null) value = BigDecimal.ZERO;
        sb.append(String.format("%-25s %13s\n", label, DF.format(value)));
    }

    private String alignCenter(String text) {
        if(text == null) return "";
        int width = 40; // 假设每行40字符
        int pad = (width - text.length()) / 2;
        if(pad <= 0) return text;
        return String.format("%" + pad + "s%s", "", text);
    }
}