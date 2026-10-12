package com.duong.flashsale.purchase.strategy;

import com.duong.flashsale.item.FlashSaleItemRepository;
import com.duong.flashsale.item.ItemNotFoundException;
import com.duong.flashsale.order.PurchaseOrder;
import com.duong.flashsale.order.PurchaseOrderRepository;
import com.duong.flashsale.purchase.PurchaseResult;
import com.duong.flashsale.purchase.PurchaseStrategy;
import com.duong.flashsale.purchase.StrategyType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SAI CÓ CHỦ ĐÍCH. Read-modify-write không khoá:
 * <pre>
 *   T1: đọc stock = 5          T2: đọc stock = 5
 *   T1: ghi stock = 4          T2: (chờ X lock của T1)
 *   T1: commit                 T2: ghi stock = 4   <- ghi đè, mất 1 lần trừ
 * </pre>
 * Kết quả: 2 đơn hàng nhưng kho chỉ giảm 1 => bán lố.
 */
@Component
public class NaiveStrategy implements PurchaseStrategy {

    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;

    public NaiveStrategy(FlashSaleItemRepository itemRepository, PurchaseOrderRepository orderRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public StrategyType type() {
        return StrategyType.NAIVE;
    }

    @Override
    @Transactional
    public PurchaseResult purchase(long userId, long itemId) {
        int available = itemRepository.findAvailableStock(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        if (available <= 0) {
            return PurchaseResult.soldOut();
        }
        itemRepository.overwriteAvailableStock(itemId, available - 1);
        PurchaseOrder order = orderRepository.saveAndFlush(new PurchaseOrder(userId, itemId, type()));
        return PurchaseResult.success(order.getId());
    }
}
