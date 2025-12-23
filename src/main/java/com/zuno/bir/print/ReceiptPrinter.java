package com.zuno.bir.print;

import com.zuno.bir.entity.*;
import com.zuno.bir.enums.BusinessType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 小票打印机类
 * 负责根据订单信息生成格式化的小票文本。
 */
public class ReceiptPrinter {

    /**
     * 小票打印的固定宽度（字符数）
     */
    private static final int WIDTH = 48;
    /**
     * 分隔线样式
     */
    private static final String SEPARATOR = "------------------------------------------------";

    /**
     * 生成小票的核心方法
     *
     * @param store    店铺信息
     * @param device   设备信息
     * @param order    销售订单
     * @param cashier  收银员信息
     * @param copyType 小票副本类型（例如，用于折扣的客户联）
     * @return 格式化后的小票字符串
     */
    public static String generateReceipt(Store store, Device device, SalesOrder order, User cashier, String copyType) {
        StringBuilder sb = new StringBuilder();

        // --- 页眉 (HEADER) ---
        printCenter(sb, store.getName());
        printCenter(sb, store.getCompanyName());
        printCenter(sb, store.getAddress());
        printCenter(sb, "VAT-REG TIN " + store.getVatRegTin()); // 税务登记号
        printCenter(sb, "SN " + device.getSn() + "   MIN# " + device.getMinNo()); // 设备序列号和机器识别号
        sb.append(SEPARATOR).append("\n");

        // --- 订单基本信息 (INFO) ---
        String siNo = order.getSiNumber() != null ? order.getSiNumber() : "SI 000000001"; // 销售发票编号
        printPair(sb, siNo, "");
        printPair(sb, "Date&Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"))); // 交易日期和时间
        sb.append(SEPARATOR).append("\n");

        // --- 餐饮特定信息 (TABLE) ---
        BusinessType bizType = BusinessType.valueOf(order.getBusinessType());
        if(bizType == BusinessType.DINING || bizType == BusinessType.FAST_FOOD) {
            if(order.getTableName() != null) printPair(sb, "Table Name:", order.getTableName()); // 桌号
            if(order.getBillingNo() != null) printPair(sb, "Billing#:", String.valueOf(order.getBillingNo())); // 账单号
            printPair(sb, "Trx Type:", order.getTrxType() != null ? order.getTrxType() : "Dine-In"); // 交易类型，如堂食
            if(order.getPax() != null && order.getPax() > 0)
                printPair(sb, "Pax:", String.valueOf(order.getPax())); // 人数
            printPair(sb, "Cashier:", cashier.getUsername()); // 收银员
            printPair(sb, "TERMINAL#:", device.getTerminalNo()); // 终端号
            sb.append(SEPARATOR).append("\n");
        }

        // --- 标题 (TITLE) ---
        printCenter(sb, "SALES INVOICE"); // 销售发票
        if(order.getTrxType() != null && order.getTrxType().contains("REPRINT")) printCenter(sb, "(REPRINT)"); // 重打印标记
        if(isNotZero(order.getGovDiscountTotal())) printCenter(sb, copyType); // 如果有政府折扣，打印副本类型
        sb.append(SEPARATOR).append("\n");

        // --- 商品列表 (ITEMS) ---
        sb.append(String.format("%-20s %4s %8s %10s %1s\n", "Description", "Qty", "U.Price", "Amount", "")); // 表头
        for(SalesOrderItem item : order.getItems()) {
            String vatFlag = "V"; // 默认为应税 (Vatable)
            if("VAT_EXEMPT".equals(item.getVatType())) vatFlag = "E"; // 免税 (Exempt)
            if("ZERO_RATED".equals(item.getVatType())) vatFlag = "Z"; // 零税率 (Zero-rated)

            BigDecimal lineGrossAmount = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            printProductLine(sb, item.getDescription(), item.getQuantity(), item.getUnitPrice(), lineGrossAmount, vatFlag);
        }
        sb.append(SEPARATOR).append("\n");

        // --- 金额计算 (FINANCIALS) ---
        printPair(sb, "Gross Sales", formatMoney(order.getGrossSales())); // 总销售额
        if(isNotZero(order.getRegularDiscount()))
            printPair(sb, "Regular Discount", formatMoney(order.getRegularDiscount())); // 常规折扣

        // 如果有政府折扣，显示详细信息
        if(isNotZero(order.getGovDiscountTotal())) {
            BigDecimal totalLessVat = BigDecimal.ZERO;
            BigDecimal totalAddVat = BigDecimal.ZERO;
            if(order.getGovDiscounts() != null) {
                for(GovernmentDiscount gd : order.getGovDiscounts()) {
                    if(gd.getLessVat() != null) totalLessVat = totalLessVat.add(gd.getLessVat());
                    if(gd.getAddVat() != null) totalAddVat = totalAddVat.add(gd.getAddVat());
                }
            }
            printPair(sb, "LESS 12% VAT", formatMoney(totalLessVat)); // 减去的12% VAT
            printPair(sb, "Discount", "-" + formatMoney(order.getGovDiscountTotal())); // 折扣金额
            printPair(sb, "Add 12% VAT", formatMoney(totalAddVat)); // 加回的12% VAT
        }

        // 动态显示服务费标签
        if(isNotZero(order.getServiceCharge())) {
            String scLabel = "Service Charge";
            if("PERCENTAGE".equalsIgnoreCase(store.getServiceChargeType()) && store.getServiceChargeValue() != null) {
                // 显示百分比, 如 Service Charge(10%)
                BigDecimal pct = store.getServiceChargeValue().multiply(new BigDecimal("100")).setScale(0, RoundingMode.DOWN);
                scLabel += "(" + pct + "%)";
            } else if("FIXED_AMOUNT".equalsIgnoreCase(store.getServiceChargeType())) {
                // 显示固定金额标记, 如 Service Charge(Fix)
                scLabel += "(Fix)";
            }
            printPair(sb, scLabel, formatMoney(order.getServiceCharge()));
        }

        sb.append("\n");
        printPairWide(sb, "Amount Due", formatMoney(order.getAmountDue())); // 应付总额
        sb.append(SEPARATOR).append("\n");

        // --- 支付信息 (PAYMENTS) ---
        if(order.getPayments() != null) {
            for(OrderPayment p : order.getPayments()) {
                if("CASH".equals(p.getType()) && isNotZero(p.getAmount()))
                    printPair(sb, "CASH", formatMoney(p.getAmount())); // 现金支付
                else if("CREDIT_CARD".equals(p.getType())) {
                    printPair(sb, "CREDIT CARD", formatMoney(p.getAmount())); // 信用卡支付
                    String masked = p.getCardNo() != null && p.getCardNo().length() > 4 ? "*******" + p.getCardNo().substring(p.getCardNo().length() - 4) : "****";
                    printPair(sb, "Credit Card No.", masked); // 掩码处理的卡号
                } else if("BALANCE".equals(p.getType())) {
                    printPair(sb, "Balance", formatMoney(p.getAmount())); // 余额支付
                    printPair(sb, "Balance Before", formatMoney(p.getBalanceBefore())); // 支付前余额
                    printPair(sb, "Balance After", formatMoney(p.getBalanceAfter())); // 支付后余额
                }
                if(isNotZero(p.getChangeAmount())) printPairWide(sb, "CHANGE", formatMoney(p.getChangeAmount())); // 找零
            }
        }
        sb.append(SEPARATOR).append("\n");

        // --- 汇总信息 (TOTALS) ---
        int totalQty = order.getItems().stream().mapToInt(SalesOrderItem::getQuantity).sum();
        printPair(sb, "Number of Items", String.valueOf(order.getItems().size())); // 商品种类数
        printPair(sb, "Total Qty", String.valueOf(totalQty)); // 商品总件数
        sb.append(SEPARATOR).append("\n");

        // --- VAT 分析 (VAT ANALYSIS) ---
        printPair(sb, "Vatable Sales", formatMoney(order.getVatableSales())); // 应税销售额
        printPair(sb, "VAT Amount(12%)", formatMoney(order.getVatAmount())); // VAT金额
        printPair(sb, "VAT Exempt Sales", formatMoney(order.getVatExemptSales())); // 免税销售额
        printPair(sb, "Zero-Rated Sales", formatMoney(order.getZeroRatedSales())); // 零税率销售额
        sb.append(SEPARATOR).append("\n");

        // --- 页脚 (FOOTER) ---
        GovernmentDiscount firstGd = (order.getGovDiscounts() != null && !order.getGovDiscounts().isEmpty()) ? order.getGovDiscounts().get(0) : null;
        printPair(sb, "Customer Name:", firstGd != null ? "Pedro Penduko" : ""); // 顾客姓名 (示例)
        printPair(sb, "Address:", firstGd != null ? "Manila City" : ""); // 顾客地址 (示例)
        printPair(sb, "TIN:", firstGd != null ? "123-456-789-000" : ""); // 顾客税号 (示例)
        // 零售业特定折扣信息
        if(bizType == BusinessType.RETAIL && isNotZero(order.getGovDiscountTotal())) {
            printPair(sb, "Previous Discount Amount", "500.00"); // 上次折扣金额 (示例)
            printPair(sb, "Current Discount Amount", formatMoney(order.getGovDiscountTotal())); // 本次折扣金额
            printPair(sb, "Remaining Weekly Quota", "1200.00"); // 本周剩余额度 (示例)
        }
        sb.append(SEPARATOR).append("\n");
        printCenter(sb, "Date of Issue: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))); // 签发日期
        printCenter(sb, "PTU No. " + "123456789"); // 打印终端使用许可证号 (示例)
        String remarks = "Note: Specific items only."; // 备注
        if(remarks != null && !remarks.isEmpty()) {
            sb.append(SEPARATOR).append("\n");
            printPair(sb, "Remarks:", remarks);
        }
        sb.append(SEPARATOR).append("\n");
        printCenter(sb, "Thank you for shopping!"); // 感谢语
        sb.append(SEPARATOR).append("\n");
        printCenter(sb, "System Provider: Zuno Solutions"); // 系统提供商
        sb.append(SEPARATOR).append("\n");
        sb.append("\n");
        printCenter(sb, "THIS SERVES AS YOUR SALES INVOICE"); // 声明
        sb.append("\n\n\n");
        return sb.toString();
    }

    // --- 辅助方法 (Helpers) ---

    /**
     * 打印居中对齐的文本行
     *
     * @param sb   StringBuilder对象
     * @param text 要打印的文本
     */
    private static void printCenter(StringBuilder sb, String text) {
        if(text == null) text = "";
        int pad = (WIDTH - text.length()) / 2;
        if(pad > 0) sb.append(String.format("%" + pad + "s", ""));
        sb.append(text).append("\n");
    }

    /**
     * 打印左右对齐的键值对
     *
     * @param sb    StringBuilder对象
     * @param label 左侧的标签
     * @param value 右侧的值
     */
    private static void printPair(StringBuilder sb, String label, String value) {
        if(value == null) return;
        int space = Math.max(1, WIDTH - label.length() - value.length());
        sb.append(label).append(String.format("%" + space + "s", "")).append(value).append("\n");
    }

    /**
     * 打印加宽间距的左右对齐键值对（通常用于突出显示）
     *
     * @param sb    StringBuilder对象
     * @param label 左侧的标签
     * @param value 右侧的值
     */
    private static void printPairWide(StringBuilder sb, String label, String value) {
        int space = Math.max(1, WIDTH - label.length() - value.length());
        sb.append(label).append(String.format("%" + space + "s", "")).append(value).append("\n");
    }

    /**
     * 打印商品信息行，处理商品描述的自动换行
     *
     * @param sb    StringBuilder对象
     * @param desc  商品描述
     * @param qty   数量
     * @param price 单价
     * @param amt   总金额
     * @param flag  税务标记 (V/E/Z)
     */
    private static void printProductLine(StringBuilder sb, String desc, Integer qty, BigDecimal price, BigDecimal amt, String flag) {
        List<String> descLines = splitString(desc, 20); // 商品描述列宽度为20
        for(int i = 0; i < descLines.size(); i++) {
            if(i == 0) {
                // 第一行打印完整信息
                sb.append(String.format("%-20s %4s %8s %10s %1s\n", descLines.get(i), qty, formatMoney(price), formatMoney(amt), flag));
            } else {
                // 后续行只打印商品描述
                sb.append(String.format("%-20s\n", descLines.get(i)));
            }
        }
    }

    /**
     * 将字符串按指定长度分割成多行
     *
     * @param str 原始字符串
     * @param len 每行的最大长度
     * @return 字符串列表
     */
    private static List<String> splitString(String str, int len) {
        List<String> list = new ArrayList<>();
        if(str == null) return list;
        while (str.length() > len) {
            list.add(str.substring(0, len));
            str = str.substring(len);
        }
        if(!str.isEmpty()) list.add(str);
        return list;
    }

    /**
     * 将BigDecimal金额格式化为两位小数的字符串
     *
     * @param amount 金额
     * @return 格式化后的字符串
     */
    private static String formatMoney(BigDecimal amount) {
        return amount == null ? "0.00" : String.format("%.2f", amount);
    }

    /**
     * 检查BigDecimal值是否不为null且不为零
     *
     * @param val 要检查的值
     * @return 如果不为零则返回true
     */
    private static boolean isNotZero(BigDecimal val) {
        return val != null && val.compareTo(BigDecimal.ZERO) != 0;
    }
}
