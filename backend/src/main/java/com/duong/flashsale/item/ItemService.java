package com.duong.flashsale.item;

import com.duong.flashsale.order.PurchaseOrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;

    public ItemService(FlashSaleItemRepository itemRepository, PurchaseOrderRepository orderRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemStats> listStats() {
        return itemRepository.findAll().stream()
                .map(item -> ItemStats.of(item, orderRepository.countByItemId(item.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemStats stats(long itemId) {
        FlashSaleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        return ItemStats.of(item, orderRepository.countByItemId(itemId));
    }

    /** Xoá toàn bộ đơn và đặt lại kho — để chạy lại bài test từ đầu. */
    @Transactional
    public ItemStats reset(long itemId, int stock) {
        orderRepository.deleteByItemId(itemId);
        if (itemRepository.resetStock(itemId, stock) == 0) {
            throw new ItemNotFoundException(itemId);
        }
        return new ItemStats(itemId, null, stock, stock, 0, 0, 0, true);
    }
}
