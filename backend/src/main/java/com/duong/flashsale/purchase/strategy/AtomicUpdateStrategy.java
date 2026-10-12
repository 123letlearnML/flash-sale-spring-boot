package com.duong.flashsale.purchase.strategy;

import com.duong.flashsale.item.FlashSaleItemRepository;
import com.duong.flashsale.order.PurchaseOrder;
import com.duong.flashsale.order.PurchaseOrderRepository;
import com.duong.flashsale.purchase.PurchaseResult;
import com.duong.flashsale.purchase.PurchaseStrategy;
import com.duong.flashsale.purchase.StrategyType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gộp "kiểm tra" và "trừ" vào 1 câu SQL:
 * <pre>UPDATE flash_sale_item SET available_stock = available_stock - 1 WHERE id = ? AND available_stock > 0</pre>
 * Không có khoảng hở giữa đọc và ghi => không có race condition, không cần retry, giữ khoá ngắn nhất.
 * Đây là cách tốt nhất khi CHỈ dùng database. Giới hạn còn lại: mọi request vẫn chạm vào DB.
 */
@Component
public class AtomicUpdateStrategy implements PurchaseStrategy {

    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;

    public AtomicUpdateStrategy(FlashSaleItemRepository itemRepository, PurchaseOrderRepository orderRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public StrategyType type() {
        return StrategyType.ATOMIC_UPDATE;
    }

    @Override
    @Transactional
    public PurchaseResult purchase(long userId, long itemId) {
        // 0 dòng bị ảnh hưởng nghĩa là hết hàng (hoặc item không tồn tại — PurchaseService đã kiểm tra trước).
        if (itemRepository.decrementIfAvailable(itemId) == 0) {
            return PurchaseResult.soldOut();
        }
        PurchaseOrder order = orderRepository.saveAndFlush(new PurchaseOrder(userId, itemId, type()));
        return PurchaseResult.success(order.getId());
    }
}
