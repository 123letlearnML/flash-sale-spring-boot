package com.duong.flashsale.purchase.strategy;

import com.duong.flashsale.item.FlashSaleItem;
import com.duong.flashsale.item.FlashSaleItemRepository;
import com.duong.flashsale.item.ItemNotFoundException;
import com.duong.flashsale.order.PurchaseOrder;
import com.duong.flashsale.order.PurchaseOrderRepository;
import com.duong.flashsale.purchase.PurchaseResult;
import com.duong.flashsale.purchase.PurchaseStrategy;
import com.duong.flashsale.purchase.StrategyType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optimistic lock: không khoá khi đọc, chỉ kiểm tra lúc ghi bằng cột version.
 * Thua thì retry trong 1 TRANSACTION MỚI (transaction cũ đang giữ snapshot cũ, retry trong đó vô ích).
 *
 * <p>Hợp với dữ liệu ít tranh chấp (sửa profile, sửa bài viết). Với flash sale — hàng nghìn request
 * cùng sửa 1 dòng — đa số sẽ xung đột và retry, tốn CPU + DB mà không làm được việc gì.
 */
@Component
public class OptimisticLockStrategy implements PurchaseStrategy {

    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;
    private final int maxRetries;
    private final Counter conflictCounter;

    public OptimisticLockStrategy(FlashSaleItemRepository itemRepository,
                                  PurchaseOrderRepository orderRepository,
                                  TransactionTemplate transactionTemplate,
                                  MeterRegistry meterRegistry,
                                  @Value("${flashsale.optimistic.max-retries}") int maxRetries) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.transactionTemplate = transactionTemplate;
        this.maxRetries = maxRetries;
        this.conflictCounter = meterRegistry.counter("flashsale.optimistic.conflicts");
    }

    @Override
    public StrategyType type() {
        return StrategyType.OPTIMISTIC;
    }

    @Override
    public PurchaseResult purchase(long userId, long itemId) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return transactionTemplate.execute(status -> attemptOnce(userId, itemId));
            } catch (ObjectOptimisticLockingFailureException e) {
                conflictCounter.increment();
                backoff(attempt);
            }
        }
        return PurchaseResult.retryExhausted();
    }

    private PurchaseResult attemptOnce(long userId, long itemId) {
        FlashSaleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        if (item.isSoldOut()) {
            return PurchaseResult.soldOut();
        }
        item.decreaseStock();
        // UPDATE ... WHERE id = ? AND version = ? — 0 dòng => ném ObjectOptimisticLockingFailureException
        itemRepository.flush();
        PurchaseOrder order = orderRepository.saveAndFlush(new PurchaseOrder(userId, itemId, type()));
        return PurchaseResult.success(order.getId());
    }

    /**
     * Exponential backoff + jitter: nếu mọi thread retry cùng lúc thì lại đụng nhau tiếp
     * ("thundering herd"). Random hoá thời gian chờ để chúng toả ra.
     */
    private void backoff(int attempt) {
        long maxDelayMs = Math.min(50, 2L << attempt);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, maxDelayMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
