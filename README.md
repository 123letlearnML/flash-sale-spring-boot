# flash-sale-spring-boot

[![ci](https://github.com/123letlearnML/flash-sale-spring-boot/actions/workflows/ci.yml/badge.svg)](https://github.com/123letlearnML/flash-sale-spring-boot/actions/workflows/ci.yml)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![java](https://img.shields.io/badge/java-21-ED8B00)

<p align="left">
<img src="https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"> <img src="https://img.shields.io/badge/Spring%20Boot%204-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4"> <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL"> <img src="https://img.shields.io/badge/Hibernate%207-59666C?style=for-the-badge&logo=hibernate&logoColor=white" alt="Hibernate 7"> <img src="https://img.shields.io/badge/Virtual%20Threads-8E44AD?style=for-the-badge" alt="Virtual Threads"> <img src="https://img.shields.io/badge/React%20%2B%20Vite-61DAFB?style=for-the-badge&logo=react&logoColor=black" alt="React + Vite">
</p>

**20.000 người tranh 1.000 vé trong vài giây.** Thử 5 cách xử lý tranh chấp tồn kho, đo bằng load test thật trên
MySQL thật, và chứng minh cách nào **bán lố**, cách nào đúng.

> **English summary.** A flash-sale backend (Spring Boot 4, Java 21 virtual threads, MySQL 9) that compares five ways
> to decrement a single hot stock row under heavy contention: naive read-modify-write, `synchronized`, pessimistic
> `SELECT ... FOR UPDATE`, optimistic `@Version` with retry, and a single conditional `UPDATE ... WHERE stock > 0`.
> Each one is load-tested with 20,000 concurrent purchase requests for 1,000 tickets, then the database is re-read
> to count oversold tickets and lost updates. The naive version sold **19,806 tickets out of 1,000**;
> `synchronized` + `@Transactional` sold **exactly 2,000** (the lock is released before the proxy commits); optimistic
> locking never oversold but turned away ~1,300 buyers while stock was still available. The conditional
> update and `SELECT ... FOR UPDATE` were both correct and statistically tied (764–928 vs 773–869 req/s over three
> runs): 95% of requests arrive after the sell-out, yet each of them still takes the row lock on the single hot row,
> which is what a cache-level "sold out" flag in phase 2 is meant to remove. The same behaviour is asserted by JUnit tests that fire 300 threads at a real
> MySQL from a starting gate (`CountDownLatch`), run in CI on every push.

## Kết quả chính

20.000 request mua, 500 request đồng thời, 1.000 vé, mỗi request là 1 user khác nhau. Sau mỗi lượt đọc lại DB để
đếm số đơn thật và đối chiếu với tồn kho.

| cách | RPS | RPS qua 3 lần chạy | p50 (ms) | p95 (ms) | p99 (ms) | đơn tạo ra | bán lố | mất cập nhật | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| NAIVE | 113 | 113 – 131 | 4.236 | 6.739 | 8.090 | **19.806** | **18.806** | 18.815 | ❌ |
| SYNCHRONIZED | 671 | 636 – 671 | 386 | 3.872 | 4.424 | **2.000** | **1.000** | 1.000 | ❌ |
| PESSIMISTIC (`FOR UPDATE`) | 802 | 773 – 869 | 399 | 2.457 | 4.648 | 1.000 | 0 | 0 | ✅ |
| OPTIMISTIC (`@Version` + retry) | 593 | 590 – 600 | 381 | 4.749 | 5.376 | 1.000 | 0 | 0 | ✅ nhưng 1.326 người bị từ chối oan |
| ATOMIC_UPDATE | 764 | 764 – 928 | 393 | 2.520 | 6.569 | 1.000 | 0 | 0 | ✅ |

"Mất cập nhật" = số đơn trừ đi số lần kho thật sự giảm. Kết quả đúng/sai lặp lại y hệt ở cả 3 lần chạy
(SYNCHRONIZED luôn ra đúng 2.000 đơn, OPTIMISTIC luôn từ chối oan ~1.300 người).

Số đo trên laptop Ryzen 5 5625U (6 nhân, 16 GB RAM), app + MySQL + trình bắn tải chạy chung 1 máy, có warm-up
JVM trước khi đo. Con số tuyệt đối không phản ánh production; **phép so sánh giữa các cách** mới là thứ đáng tin.

Rút ra:
- **NAIVE vừa sai vừa chậm nhất.** Lost update làm tồn kho gần như không giảm, nên ai cũng "mua được": mỗi request
  đều phải INSERT đơn và tranh khoá, không có request nào bị từ chối sớm.
- **`synchronized` + `@Transactional` bán đúng gấp đôi.** Proxy của `@Transactional` bọc *ngoài* method, nên khoá
  được nhả *trước* khi commit. Thread tiếp theo đọc được tồn kho cũ, đúng 1 lần cho mỗi lượt giảm, nên ra **đúng 2.000**,
  lặp lại y hệt ở mọi lần chạy.
- **Optimistic lock đúng nhưng từ chối oan.** Với 1 dòng nóng, đa số request thua cuộc đua version; hết lượt retry
  thì khách bị từ chối **trong khi kho vẫn còn vé**.
- **ATOMIC_UPDATE và PESSIMISTIC hoà nhau** trong phạm vi nhiễu (764–928 so với 773–869 RPS). Mình đã kỳ vọng
  1 câu `UPDATE ... WHERE available_stock > 0` nhanh hơn rõ rệt, nhưng số đo không ủng hộ: 19.000/20.000 request đến
  **sau khi đã hết vé**, mà cả 2 cách vẫn lấy X lock trên đúng 1 dòng tồn kho cho từng request đó (ở REPEATABLE READ,
  InnoDB giữ khoá dòng đã quét tới khi commit, kể cả khi điều kiện `WHERE` không khớp). Nút cổ chai là **dòng nóng**,
  không phải cách viết SQL. Hướng sửa: cờ "hết hàng" trong bộ nhớ/Redis để không chạm DB nữa (phase 2).

## Cách hoạt động

```
POST /api/items/1/purchases {userId, strategy}
   │
   ├─► PurchaseService: đã mua chưa? (đọc index uk_orders_user_item, chưa chạm dòng tồn kho)
   │
   ├─► Strategy (1 transaction ngắn)
   │      NAIVE          SELECT stock            → UPDATE stock = :giá_trị_cũ - 1     ❌ lost update
   │      SYNCHRONIZED   synchronized { như NAIVE }  (khoá nhả trước COMMIT)          ❌
   │      PESSIMISTIC    SELECT ... FOR UPDATE   → UPDATE                             ✅ xếp hàng
   │      OPTIMISTIC     SELECT + @Version       → UPDATE ... WHERE version = ?       ✅ retry
   │      ATOMIC_UPDATE  UPDATE ... SET stock = stock - 1 WHERE stock > 0             ✅ 1 câu
   │      └─► INSERT order   (UNIQUE (user_id, item_id): mua trùng thì rollback cả phần trừ kho)
   │
   └─► 201 SUCCESS │ 409 SOLD_OUT / ALREADY_PURCHASED │ 503 quá tải (fail fast sau 5 s)
```

- **Strategy pattern**: mỗi cách là 1 bean implement `PurchaseStrategy`, thêm cách mới không sửa code cũ.
- **Ràng buộc ở DB, không ở app**: `UNIQUE (user_id, item_id)` và `CHECK (available_stock >= 0)` đúng kể cả khi
  chạy 10 instance. Kiểm tra ở app chỉ để chặn sớm cho nhanh.
- **Thứ tự lấy khoá**: flush UPDATE (X lock) *trước* INSERT order. INSERT có foreign key đặt S lock lên dòng
  tồn kho, mà Hibernate mặc định flush INSERT trước UPDATE, nên 2 transaction cùng giữ S rồi cùng chờ X: deadlock.
- **Fail fast**: Hikari chờ connection tối đa 5 s rồi trả **503**, không treo request 30 s.
- **Virtual threads** (Java 21): Tomcat nhận hàng nghìn request đồng thời mà không cần hàng nghìn OS thread.
  Nút cổ chai vẫn là connection pool và dòng tồn kho, đo ở bảng dưới.

Giải thích chi tiết từng khái niệm (MVCC, consistent read vs current read, deadlock do FK...) và câu hỏi phỏng vấn:
**[KNOWLEDGE.md](KNOWLEDGE.md)**.

## Kết quả chi tiết

**Kích thước connection pool** (ATOMIC_UPDATE, mỗi cấu hình chạy 3 lần, có warm-up):

| pool size | RPS trung bình | khoảng 3 lần | p95 trung bình (ms) |
|---:|---:|---:|---:|
| 5 | 601 | 445 – 795 | 2.652 |
| 10 | 676 | 610 – 731 | 2.629 |
| 20 (mặc định) | 690 | 618 – 802 | 2.479 |
| 50 | 678 | 532 – 829 | 2.783 |
| 100 | 731 | 695 – 763 | 2.479 |

**Không có khác biệt đo được.** Chênh lệch giữa các pool size (~600–730 RPS) nhỏ hơn chênh lệch giữa 2 lần chạy
của *cùng* 1 cấu hình (tới ~350 RPS). Hợp lý: mọi request đều phải đi qua X lock của 1 dòng duy nhất, nên tại 1 thời
điểm chỉ 1 transaction tiến lên được. Thêm connection chỉ chuyển chỗ xếp hàng từ Hikari vào InnoDB. Bài học:
**tăng pool không chữa được hot row**; phải giảm số request chạm vào dòng đó.

**Những lần mình đo sai** (ghi lại vì học được nhiều nhất):
- **Quên warm-up JVM.** Lần đầu đo pool size, mỗi cấu hình khởi động lại app rồi đo ngay: mọi cấu hình chỉ ~550 RPS,
  trong khi cùng code đó chạy cuối danh sách (JVM đã "nóng") được ~900 RPS. JIT chưa biên dịch đường code nóng thì
  cấu hình nào cũng chậm như nhau. Giờ script luôn chạy 10.000 request warm-up rồi mới đo.
- **1 lần đo là không đủ.** Cùng 1 cấu hình, 2 lần chạy chênh nhau tới ~30% vì app, MySQL và Node bắn tải tranh CPU
  của cùng 1 laptop. Giờ mỗi cấu hình chạy 3 lần, báo cả trung bình lẫn khoảng.
- **Lỗi 500 lẽ ra là 503.** Lần chạy đầu có ~350 lỗi 500. Đọc log thì thấy đều là hết connection, nhưng xảy ra ở
  bước kiểm tra "đã mua chưa" (ngoài transaction) nên Spring ném `DataAccessResourceFailureException` thay vì
  `CannotCreateTransactionException` mà handler đang bắt. Quá tải phải là 503 để client biết retry.

## Kiểm chứng tự động

`backend/src/test/java/.../PurchaseConcurrencyTest.java`: 300 virtual thread đứng chờ ở `CountDownLatch` rồi được
thả cùng lúc vào **MySQL thật** (không mock, không H2, vì H2 khoá khác MySQL). Test chứng minh **cả hai chiều**:

| test | kiểm tra |
|---|---|
| `correctStrategiesSellExactlyTheStock` (PESSIMISTIC, ATOMIC_UPDATE) | bán đúng 50/50 vé, kho = 0, không lệch |
| `optimisticNeverOversellsButMayLeaveStockUnsold` | không bán lố, không mất cập nhật |
| `brokenStrategiesLoseUpdates` (NAIVE, SYNCHRONIZED) | **phải** lộ lỗi lost update |

CI (GitHub Actions) chạy các test này trên MySQL 8.4 ở mỗi lần push.

## Chạy thử

Cần: JDK 21+, Node 18+, MySQL 8.4+ đã cài (không cần Docker).

```powershell
# 1. MySQL riêng cho dự án ở cổng 3307 (dùng lại mysqld.exe đã cài, thư mục dữ liệu riêng, không cần mật khẩu root)
.\scripts\dev-mysql.ps1

# 2. Backend: Flyway tự tạo bảng + seed 100.000 user
cd backend
./mvnw spring-boot:run

# 3. Giao diện theo dõi tồn kho realtime: http://localhost:5173
cd frontend
npm install
npm run dev

# 4. Load test
cd load-test
node flash-sale-load.mjs --all --requests 20000 --concurrency 500
```

Đã có MySQL sẵn thì bỏ bước 1, chạy `scripts/mysql-setup.sql` bằng root rồi đặt
`DB_URL=jdbc:mysql://localhost:3306/flash_sale`.

Test: `cd backend && ./mvnw test` (cần MySQL đang chạy).

## API

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/items/{id}/purchases` | Mua. Body `{userId, strategy}` → 201 / 409 / 503 |
| `GET` | `/api/items` | Tồn kho, số đơn, bán lố, mất cập nhật của mọi item |
| `GET` | `/api/items/{id}/stats` | Như trên cho 1 item |
| `POST` | `/api/admin/items/{id}/reset` | Xoá đơn, đặt lại kho `{stock}` |
| `GET` | `/actuator/metrics/flashsale.purchase` | Thời gian xử lý theo strategy và kết quả |

## Cấu trúc

```
backend/src/main/java/com/duong/flashsale/
├── purchase/                 PurchaseService (điều phối), PurchaseController, StrategyType
│   └── strategy/             5 cách: Naive, Synchronized, PessimisticLock, OptimisticLock, AtomicUpdate
├── item/                     FlashSaleItem (@Version), repository với các câu khoá, thống kê
├── order/                    PurchaseOrder, unique (user_id, item_id)
└── common/                   GlobalExceptionHandler: 404 / 409 / 503
backend/src/main/resources/db/migration/   Flyway: schema + seed 100k user bằng recursive CTE
load-test/flash-sale-load.mjs              bắn tải + đọc lại DB để kiểm tra bán lố
frontend/                                  React + Vite: tiến độ bán, cảnh báo đỏ khi bán lố
```

## Lộ trình

- [x] Phase 1: 5 chiến lược ở tầng database, test đồng thời, load test, đo pool size
- [ ] Phase 2: trừ kho bằng Redis Lua script, cờ "hết hàng" trong bộ nhớ để không chạm DB
- [ ] Phase 3: nhận đơn bất đồng bộ (Redis Streams) + Transactional Outbox
- [ ] Phase 4: giữ vé 10 phút chờ thanh toán, quá hạn tự trả kho
- [ ] Phase 5: waiting room (hàng đợi ảo) bằng Redis Sorted Set

Dự án đi cùng: [ewallet-ledger-spring-boot](https://github.com/123letlearnML/ewallet-ledger-spring-boot):
chuyển tiền đồng thời không mất 1 đồng (sổ cái kép, deadlock, idempotency).
