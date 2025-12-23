package com.zuno.bir.dto;

import com.zuno.bir.entity.SalesOrder;
import lombok.Data;

/**
 * 订单预览响应
 * <p>
 * 用于前端实时预览政府折扣和发票内容：
 * - 携带计算后的完整订单对象（含金额、税、折扣）
 * - 携带生成好的小票文本内容，前端可直接渲染或打印预览
 */
@Data
public class OrderPreviewResponse {

    /**
     * 计算完成后的订单
     */
    private SalesOrder order;

    /**
     * 发票/小票文本内容
     */
    private String receiptContent;
}


