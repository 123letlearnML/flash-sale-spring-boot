package com.duong.flashsale.purchase;

/**
 * Strategy pattern: mỗi cách chống bán lố là 1 bean. Thêm cách mới (ví dụ Redis Lua ở phase 2)
 * chỉ cần thêm 1 class implement interface này, không sửa code cũ (Open/Closed principle).
 */
public interface PurchaseStrategy {

    StrategyType type();

    /**
     * Trừ 1 đơn vị kho và tạo đơn hàng.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException nếu user đã mua (vi phạm unique key)
     */
    PurchaseResult purchase(long userId, long itemId);
}
