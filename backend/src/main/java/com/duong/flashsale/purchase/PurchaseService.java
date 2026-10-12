package com.duong.flashsale.purchase;

import com.duong.flashsale.item.FlashSaleItemRepository;
import com.duong.flashsale.item.ItemNotFoundException;
import com.duong.flashsale.order.PurchaseOrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Điều phối: kiểm tra nhanh rồi giao cho strategy tương ứng.
 * Cố ý KHÔNG có @Transactional ở đây — transaction nằm trong từng strategy, càng ngắn càng tốt.
 */
@Service
public class PurchaseService {

    private static final String DUPLICATE_ORDER_CONSTRAINT = "uk_orders_user_item";

    private final Map<StrategyType, PurchaseStrategy> strategies = new EnumMap<>(StrategyType.class);
    private final FlashSaleItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;
    private final MeterRegistry meterRegistry;

    /** Item không bao giờ bị xoá trong hệ thống này nên cache id đã biết là an toàn. */
    private final Set<Long> knownItemIds = ConcurrentHashMap.newKeySet();

    public PurchaseService(List<PurchaseStrategy> strategyBeans,
                           FlashSaleItemRepository itemRepository,
                           PurchaseOrderRepository orderRepository,
                           MeterRegistry meterRegistry) {
        strategyBeans.forEach(s -> strategies.put(s.type(), s));
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.meterRegistry = meterRegistry;
    }

    public PurchaseResult purchase(StrategyType type, long userId, long itemId) {
        ensureItemExists(itemId);
        Timer.Sample sample = Timer.start(meterRegistry);

        PurchaseResult result = doPurchase(type, userId, itemId);

        sample.stop(meterRegistry.timer("flashsale.purchase",
                "strategy", type.name(), "outcome", result.outcome().name()));
        return result;
    }

    private PurchaseResult doPurchase(StrategyType type, long userId, long itemId) {
        // Fast path: user đã mua thì từ chối luôn, không đụng vào dòng tồn kho đang "nóng".
        // Chỉ là tối ưu — 2 request cùng user vẫn có thể lọt qua đây cùng lúc, unique key ở DB mới là chốt chặn.
        if (orderRepository.existsByUserIdAndItemId(userId, itemId)) {
            return PurchaseResult.alreadyPurchased();
        }
        try {
            return strategies.get(type).purchase(userId, itemId);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateOrder(e)) {
                return PurchaseResult.alreadyPurchased(); // transaction đã rollback => kho được hoàn lại
            }
            throw e;
        }
    }

    private void ensureItemExists(long itemId) {
        if (knownItemIds.contains(itemId)) {
            return;
        }
        if (!itemRepository.existsById(itemId)) {
            throw new ItemNotFoundException(itemId);
        }
        knownItemIds.add(itemId);
    }

    private static boolean isDuplicateOrder(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(DUPLICATE_ORDER_CONSTRAINT);
    }
}
