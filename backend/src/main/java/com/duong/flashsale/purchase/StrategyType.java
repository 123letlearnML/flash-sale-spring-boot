package com.duong.flashsale.purchase;

/** Các cách xử lý tranh chấp tồn kho. Hai cách đầu SAI có chủ đích để đo được lỗi. */
public enum StrategyType {
    /** Đọc rồi ghi đè, không khoá => lost update => bán lố. */
    NAIVE,
    /** synchronized + @Transactional => vẫn bán lố (khoá nhả trước khi commit). */
    SYNCHRONIZED,
    /** SELECT ... FOR UPDATE. Đúng, nhưng mọi request xếp hàng chờ khoá. */
    PESSIMISTIC,
    /** @Version + retry. Đúng, nhưng tranh chấp cao thì retry liên tục. */
    OPTIMISTIC,
    /** UPDATE ... WHERE available_stock > 0 trong 1 câu SQL. Đúng và nhanh nhất ở tầng DB. */
    ATOMIC_UPDATE
}
