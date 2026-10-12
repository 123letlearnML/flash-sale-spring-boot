package com.duong.flashsale.item;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    /**
     * Đọc số tồn kho bằng "consistent read" (snapshot MVCC) — KHÔNG khoá gì cả.
     * Dùng cho chiến lược NAIVE để minh hoạ lỗi lost update.
     */
    @Query("select i.availableStock from FlashSaleItem i where i.id = :id")
    Optional<Integer> findAvailableStock(@Param("id") long id);

    /** Ghi đè giá trị tuyệt đối — chính câu này gây lost update khi chạy song song. */
    @Modifying
    @Query("update FlashSaleItem i set i.availableStock = :stock where i.id = :id")
    int overwriteAvailableStock(@Param("id") long id, @Param("stock") int stock);

    /** SELECT ... FOR UPDATE: khoá độc quyền (X lock) dòng này tới khi transaction kết thúc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FlashSaleItem i where i.id = :id")
    Optional<FlashSaleItem> findByIdForUpdate(@Param("id") long id);

    /**
     * Trừ kho nguyên tử trong 1 câu SQL. Điều kiện "available_stock > 0" được InnoDB kiểm tra
     * trên bản ghi MỚI NHẤT (current read) trong lúc giữ X lock => không thể bán lố.
     *
     * @return 1 nếu trừ thành công, 0 nếu đã hết hàng
     */
    @Modifying
    @Query("""
            update FlashSaleItem i
               set i.availableStock = i.availableStock - 1,
                   i.version = i.version + 1
             where i.id = :id and i.availableStock > 0
            """)
    int decrementIfAvailable(@Param("id") long id);

    @Modifying
    @Query("""
            update FlashSaleItem i
               set i.totalStock = :stock, i.availableStock = :stock, i.version = 0
             where i.id = :id
            """)
    int resetStock(@Param("id") long id, @Param("stock") int stock);
}
