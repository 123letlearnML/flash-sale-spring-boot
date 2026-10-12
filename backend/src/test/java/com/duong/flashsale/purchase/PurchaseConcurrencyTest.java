package com.duong.flashsale.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.duong.flashsale.item.ItemService;
import com.duong.flashsale.item.ItemStats;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Bắn nhiều request THẬT SỰ đồng thời vào MySQL thật (không mock, không H2 — H2 khoá khác MySQL).
 * CountDownLatch giữ mọi thread ở vạch xuất phát rồi thả cùng lúc để tối đa hoá tranh chấp.
 */
@SpringBootTest
@ActiveProfiles("test")
class PurchaseConcurrencyTest {

    private static final long ITEM_ID = 1L;
    private static final int STOCK = 50;
    private static final int BUYERS = 300;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private ItemService itemService;

    @ParameterizedTest
    @EnumSource(value = StrategyType.class, names = {"PESSIMISTIC", "ATOMIC_UPDATE"})
    void correctStrategiesSellExactlyTheStock(StrategyType strategy) throws Exception {
        ItemStats stats = runFlashSale(strategy);

        assertThat(stats.orderCount()).isEqualTo(STOCK);
        assertThat(stats.availableStock()).isZero();
        assertThat(stats.consistent()).isTrue();
    }

    @Test
    void optimisticNeverOversellsButMayLeaveStockUnsold() throws Exception {
        ItemStats stats = runFlashSale(StrategyType.OPTIMISTIC);

        // Không bán lố và không mất cập nhật...
        assertThat(stats.consistent()).isTrue();
        // ...nhưng có thể có người bị từ chối (RETRY_EXHAUSTED) trong khi kho vẫn còn hàng.
        assertThat(stats.orderCount()).isLessThanOrEqualTo(STOCK);
    }

    @ParameterizedTest
    @EnumSource(value = StrategyType.class, names = {"NAIVE", "SYNCHRONIZED"})
    void brokenStrategiesLoseUpdates(StrategyType strategy) throws Exception {
        // Race condition không xảy ra 100% mỗi lần => chạy vài vòng, chỉ cần 1 vòng lộ lỗi.
        boolean bugReproduced = false;
        for (int round = 0; round < 5 && !bugReproduced; round++) {
            bugReproduced = !runFlashSale(strategy).consistent();
        }
        assertThat(bugReproduced)
                .as("%s phải để lộ lỗi lost update khi chạy song song", strategy)
                .isTrue();
    }

    private ItemStats runFlashSale(StrategyType strategy) throws Exception {
        itemService.reset(ITEM_ID, STOCK);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<PurchaseResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long userId = 1; userId <= BUYERS; userId++) {
                long buyer = userId;
                futures.add(executor.submit(() -> {
                    startGun.await();
                    return purchaseService.purchase(strategy, buyer, ITEM_ID);
                }));
            }
            startGun.countDown();
            for (Future<PurchaseResult> future : futures) {
                future.get();
            }
        }
        return itemService.stats(ITEM_ID);
    }
}
