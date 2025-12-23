package com.zuno.bir.engine.reverse;

import com.zuno.bir.entity.SalesOrder;
import com.zuno.bir.entity.SalesOrderItem;
import com.zuno.bir.enums.ReverseType;

import java.util.List;

/**
 * 订单逆向操作服务
 * <p>
 * 处理订单的逆向操作：作废、退货
 */
public class OrderReverseService {
    
    /**
     * 作废订单
     * <p>
     * 作废整个订单，所有金额变为负数或零。
     * 作废后的订单金额应该与原始订单金额完全抵消。
     *
     * @param originalOrder 原始订单
     * @param voidOrder 作废订单（新创建的订单，用于记录作废操作）
     * @return 作废后的订单
     */
    public static SalesOrder voidOrder(SalesOrder originalOrder, SalesOrder voidOrder) {
        if (originalOrder == null) {
            throw new IllegalArgumentException("原始订单不能为空");
        }
        
        // 设置作废订单的基本信息
        voidOrder.setBusinessType(originalOrder.getBusinessType());
        voidOrder.setStoreId(originalOrder.getStoreId());
        voidOrder.setDeviceId(originalOrder.getDeviceId());
        voidOrder.setCashierId(originalOrder.getCashierId());
        voidOrder.setTrxType("VOID"); // 交易类型：作废
        
        // 复制商品项，但金额取负数
        if (originalOrder.getItems() != null) {
            List<SalesOrderItem> voidItems = voidOrder.getItems();
            if (voidItems != null) {
                for (int i = 0; i < originalOrder.getItems().size() && i < voidItems.size(); i++) {
                    SalesOrderItem originalItem = originalOrder.getItems().get(i);
                    SalesOrderItem voidItem = voidItems.get(i);
                    
                    // 金额取负数
                    if (originalItem.getAmount() != null) {
                        voidItem.setAmount(originalItem.getAmount().negate());
                    }
                    if (originalItem.getVatableSales() != null) {
                        voidItem.setVatableSales(originalItem.getVatableSales().negate());
                    }
                    if (originalItem.getVatAmount() != null) {
                        voidItem.setVatAmount(originalItem.getVatAmount().negate());
                    }
                    if (originalItem.getVatExemptSales() != null) {
                        voidItem.setVatExemptSales(originalItem.getVatExemptSales().negate());
                    }
                    if (originalItem.getZeroRatedSales() != null) {
                        voidItem.setZeroRatedSales(originalItem.getZeroRatedSales().negate());
                    }
                    if (originalItem.getRegularDiscount() != null) {
                        voidItem.setRegularDiscount(originalItem.getRegularDiscount().negate());
                    }
                }
            }
        }
        
        // 订单总额取负数
        if (originalOrder.getGrossSales() != null) {
            voidOrder.setGrossSales(originalOrder.getGrossSales().negate());
        }
        if (originalOrder.getRegularDiscount() != null) {
            voidOrder.setRegularDiscount(originalOrder.getRegularDiscount().negate());
        }
        if (originalOrder.getGovDiscountTotal() != null) {
            voidOrder.setGovDiscountTotal(originalOrder.getGovDiscountTotal().negate());
        }
        if (originalOrder.getVatableSales() != null) {
            voidOrder.setVatableSales(originalOrder.getVatableSales().negate());
        }
        if (originalOrder.getVatAmount() != null) {
            voidOrder.setVatAmount(originalOrder.getVatAmount().negate());
        }
        if (originalOrder.getVatExemptSales() != null) {
            voidOrder.setVatExemptSales(originalOrder.getVatExemptSales().negate());
        }
        if (originalOrder.getZeroRatedSales() != null) {
            voidOrder.setZeroRatedSales(originalOrder.getZeroRatedSales().negate());
        }
        if (originalOrder.getServiceCharge() != null) {
            voidOrder.setServiceCharge(originalOrder.getServiceCharge().negate());
        }
        if (originalOrder.getAmountDue() != null) {
            voidOrder.setAmountDue(originalOrder.getAmountDue().negate());
        }
        
        return voidOrder;
    }
    
    /**
     * 退货订单
     * <p>
     * 部分或全部退货，创建退货订单。
     * 退货订单的金额为负数，用于抵消原始订单。
     *
     * @param originalOrder 原始订单
     * @param returnOrder 退货订单（新创建的订单，用于记录退货操作）
     * @param returnItems 退货商品项列表（包含要退货的商品和数量）
     * @return 退货后的订单
     */
    public static SalesOrder returnOrder(SalesOrder originalOrder, SalesOrder returnOrder, 
                                        List<SalesOrderItem> returnItems) {
        if (originalOrder == null) {
            throw new IllegalArgumentException("原始订单不能为空");
        }
        
        if (returnItems == null || returnItems.isEmpty()) {
            throw new IllegalArgumentException("退货商品项不能为空");
        }
        
        // 设置退货订单的基本信息
        returnOrder.setBusinessType(originalOrder.getBusinessType());
        returnOrder.setStoreId(originalOrder.getStoreId());
        returnOrder.setDeviceId(originalOrder.getDeviceId());
        returnOrder.setCashierId(originalOrder.getCashierId());
        returnOrder.setTrxType("RETURN"); // 交易类型：退货
        returnOrder.setItems(returnItems);

        // 计算退货金额（部分或全部退货）
        // 按原订单中对应行的金额按数量比例分摊，然后取负数
        java.math.BigDecimal totalGross = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalRegDisc = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalGovDisc = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalVatable = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalVatExempt = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalZeroRated = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalVatAmount = java.math.BigDecimal.ZERO;

        if (originalOrder.getItems() != null) {
            for (SalesOrderItem retItem : returnItems) {
                // 使用 itemId 优先匹配，其次 productId
                SalesOrderItem src = null;
                for (SalesOrderItem orig : originalOrder.getItems()) {
                    if (retItem.getItemId() != null && retItem.getItemId().equals(orig.getItemId())) {
                        src = orig;
                        break;
                    }
                    if (src == null && retItem.getProductId() != null
                            && retItem.getProductId().equals(orig.getProductId())) {
                        src = orig;
                    }
                }
                if (src == null) {
                    continue;
                }

                int origQty = src.getQuantity() != null ? src.getQuantity() : 0;
                int retQty = retItem.getQuantity() != null ? retItem.getQuantity() : 0;
                if (origQty == 0 || retQty == 0) {
                    continue;
                }

                // 退货数量统一按负数处理
                int absRetQty = Math.abs(retQty);
                retItem.setQuantity(-absRetQty);

                java.math.BigDecimal qtyRatio =
                        new java.math.BigDecimal(absRetQty)
                                .divide(new java.math.BigDecimal(origQty), 4, java.math.RoundingMode.HALF_UP);

                // 单价保持与原订单一致
                retItem.setUnitPrice(src.getUnitPrice());
                java.math.BigDecimal lineGross = src.getUnitPrice()
                        .multiply(new java.math.BigDecimal(absRetQty))
                        .negate(); // 销售额为负

                // 按比例分摊各类金额并取负数
                java.math.BigDecimal regDisc = negateProportion(src.getRegularDiscount(), qtyRatio);
                java.math.BigDecimal govDisc = negateProportion(originalOrder.getGovDiscountTotal(), qtyRatio); // 近似分摊
                java.math.BigDecimal vatable = negateProportion(src.getVatableSales(), qtyRatio);
                java.math.BigDecimal vatAmt = negateProportion(src.getVatAmount(), qtyRatio);
                java.math.BigDecimal vatExempt = negateProportion(src.getVatExemptSales(), qtyRatio);
                java.math.BigDecimal zeroRated = negateProportion(src.getZeroRatedSales(), qtyRatio);

                // 回填到退货明细行
                retItem.setAmount(negateProportion(src.getAmount(), qtyRatio));
                retItem.setRegularDiscount(regDisc);
                retItem.setVatableSales(vatable);
                retItem.setVatAmount(vatAmt);
                retItem.setVatExemptSales(vatExempt);
                retItem.setZeroRatedSales(zeroRated);

                // 汇总到退货订单
                totalGross = totalGross.add(lineGross);
                totalRegDisc = totalRegDisc.add(regDisc);
                totalGovDisc = totalGovDisc.add(govDisc);
                totalVatable = totalVatable.add(vatable);
                totalVatAmount = totalVatAmount.add(vatAmt);
                totalVatExempt = totalVatExempt.add(vatExempt);
                totalZeroRated = totalZeroRated.add(zeroRated);
            }
        }

        // 退货单不再单独计提服务费和本地税，直接按行金额汇总
        returnOrder.setServiceCharge(java.math.BigDecimal.ZERO);
        returnOrder.setLocalTax(java.math.BigDecimal.ZERO);

        // 填充订单汇总金额
        returnOrder.setGrossSales(totalGross);
        returnOrder.setRegularDiscount(totalRegDisc);
        returnOrder.setGovDiscountTotal(totalGovDisc);
        returnOrder.setVatableSales(totalVatable);
        returnOrder.setVatAmount(totalVatAmount);
        returnOrder.setVatExemptSales(totalVatExempt);
        returnOrder.setZeroRatedSales(totalZeroRated);

        // 应退金额（负数）
        java.math.BigDecimal amountDue = java.math.BigDecimal.ZERO;
        if (returnOrder.getItems() != null) {
            for (SalesOrderItem item : returnOrder.getItems()) {
                if (item.getAmount() != null) {
                    amountDue = amountDue.add(item.getAmount());
                }
            }
        }
        returnOrder.setAmountDue(amountDue);

        return returnOrder;
    }

    /**
     * 按数量比例分摊金额并取负数（空值安全）
     */
    private static java.math.BigDecimal negateProportion(java.math.BigDecimal src,
                                                         java.math.BigDecimal ratio) {
        if (src == null) {
            return java.math.BigDecimal.ZERO;
        }
        return src.multiply(ratio)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .negate();
    }
}

