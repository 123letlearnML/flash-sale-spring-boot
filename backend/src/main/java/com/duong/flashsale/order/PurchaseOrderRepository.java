package com.duong.flashsale.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /** Dùng index uk_orders_user_item => chỉ đọc index, không chạm vào bảng. */
    boolean existsByUserIdAndItemId(long userId, long itemId);

    long countByItemId(long itemId);

    @Modifying
    @Query("delete from PurchaseOrder o where o.itemId = :itemId")
    int deleteByItemId(@Param("itemId") long itemId);
}
