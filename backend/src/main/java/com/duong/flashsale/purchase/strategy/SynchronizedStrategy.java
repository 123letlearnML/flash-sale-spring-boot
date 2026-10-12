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
 * SAI CÓ CHỦ ĐÍCH — lỗi kinh điển hay bị hỏi khi phỏng vấn.
 *
 * <p>@Transactional chạy bằng proxy BAO BÊN NGOÀI method:
 * <pre>
 *   proxy: BEGIN
 *     synchronized: lock -> đọc, ghi -> unlock     <- thread khác vào được từ đây
 *   proxy: COMMIT                                   <- nhưng dữ liệu mới commit ở đây
 * </pre>
 * Thread tiếp theo đọc được giá trị CŨ (chưa commit) => vẫn bán lố.
 * Ngoài ra synchronized chỉ có tác dụng trong 1 JVM: chạy 2 instance app là hỏng hoàn toàn.
 */
@Component
public class SynchronizedStrategy implements PurchaseStrategy {

    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;

    public SynchronizedStrategy(FlashSaleItemRepository itemRepository, PurchaseOrderRepository orderRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public StrategyType type() {
        return StrategyType.SYNCHRONIZED;
    }

    @Override
    @Transactional
    public synchronized PurchaseResult purchase(long userId, long itemId) {
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
