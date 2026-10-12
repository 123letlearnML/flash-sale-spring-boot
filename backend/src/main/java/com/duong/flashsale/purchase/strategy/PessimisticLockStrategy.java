package com.duong.flashsale.purchase.strategy;

import com.duong.flashsale.item.FlashSaleItem;
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
 * SELECT ... FOR UPDATE. Transaction nào lấy được X lock thì làm, còn lại xếp hàng.
 * Đúng 100%, chạy được nhiều instance, nhưng throughput bị giới hạn vì mọi request đi tuần tự
 * qua 1 dòng và mỗi request giữ khoá suốt thời gian transaction (gồm cả INSERT order).
 */
@Component
public class PessimisticLockStrategy implements PurchaseStrategy {

    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;

    public PessimisticLockStrategy(FlashSaleItemRepository itemRepository, PurchaseOrderRepository orderRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public StrategyType type() {
        return StrategyType.PESSIMISTIC;
    }

    @Override
    @Transactional
    public PurchaseResult purchase(long userId, long itemId) {
        FlashSaleItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        if (item.isSoldOut()) {
            return PurchaseResult.soldOut();
        }
        item.decreaseStock();
        // Flush UPDATE trước INSERT. Mặc định Hibernate flush INSERT trước UPDATE — xem mục
        // "Deadlock do foreign key" trong KNOWLEDGE.md để hiểu vì sao thứ tự này quan trọng.
        itemRepository.flush();
        PurchaseOrder order = orderRepository.saveAndFlush(new PurchaseOrder(userId, itemId, type()));
        return PurchaseResult.success(order.getId());
    }
}
