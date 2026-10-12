package com.duong.flashsale.item;

/**
 * Ảnh chụp tình trạng 1 item, dùng để kiểm chứng tính đúng đắn sau load test.
 *
 * @param oversold    số đơn vượt quá tổng kho (phải luôn = 0)
 * @param lostUpdates số lần trừ kho bị "mất": có đơn hàng nhưng kho không giảm (phải luôn = 0)
 */
public record ItemStats(long itemId,
                        String name,
                        int totalStock,
                        int availableStock,
                        long orderCount,
                        long oversold,
                        long lostUpdates,
                        boolean consistent) {

    public static ItemStats of(FlashSaleItem item, long orderCount) {
        long decremented = item.getTotalStock() - item.getAvailableStock();
        long oversold = Math.max(0, orderCount - item.getTotalStock());
        long lostUpdates = orderCount - decremented;
        return new ItemStats(item.getId(), item.getName(), item.getTotalStock(), item.getAvailableStock(),
                orderCount, oversold, lostUpdates, oversold == 0 && lostUpdates == 0);
    }
}
