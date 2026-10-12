# Flash Sale — Tổng hợp kiến thức

> **Bài toán:** 1.000 vé, 20.000+ người bấm "Mua" trong cùng 1 giây.
> Yêu cầu: không bán lố 1 vé nào, mỗi người chỉ mua 1 lần, hệ thống không sập.

Tài liệu này để **học và ôn phỏng vấn**. Đọc theo thứ tự, mỗi phần có: vấn đề → cách giải → code ở đâu → câu hỏi phỏng vấn hay gặp.

---

## Mục lục
1. [Thiết kế database](#1-thiết-kế-database)
2. [Race condition và Lost update](#2-race-condition-và-lost-update)
3. [MVCC: consistent read và current read](#3-mvcc-consistent-read-và-current-read)
4. [5 chiến lược và vì sao đúng/sai](#4-5-chiến-lược)
5. [Bẫy: synchronized + @Transactional](#5-bẫy-synchronized--transactional)
6. [Chống mua trùng: unique key là chốt chặn cuối](#6-chống-mua-trùng)
7. [Deadlock do foreign key](#7-deadlock-do-foreign-key)
8. [Connection pool, virtual threads, fail fast](#8-connection-pool-virtual-threads-fail-fast)
9. [Cách kiểm chứng: test đồng thời và load test](#9-cách-kiểm-chứng)
10. [Kết quả benchmark](#10-kết-quả-benchmark)
11. [Câu hỏi phỏng vấn](#11-câu-hỏi-phỏng-vấn)
12. [Lộ trình các phase tiếp theo](#12-lộ-trình)

---

## 1. Thiết kế database

File: `backend/src/main/resources/db/migration/V1__init_schema.sql`

```
users (100.000 dòng)        flash_sale_item (dòng "nóng")        orders
- id PK                      - id PK                              - id PK
- username UNIQUE            - total_stock                        - user_id  FK
- email UNIQUE               - available_stock  CHECK >= 0        - item_id  FK
                             - version (optimistic lock)          - UNIQUE (user_id, item_id)
```

| Quyết định | Lý do |
|---|---|
| Tiền lưu `BIGINT` (VND) | `float/double` sai số nhị phân: `0.1 + 0.2 != 0.3`. Không bao giờ dùng số thực cho tiền. |
| `available_stock` tách khỏi `total_stock` | So sánh `total - available` với số đơn để **phát hiện mất cập nhật**. |
| `CHECK (available_stock >= 0)` | Lưới an toàn cuối. Nhưng **không chặn được lost update**, xem mục 2. |
| `UNIQUE (user_id, item_id)` | Luật "mỗi người mua 1 lần" đặt ở DB nên vẫn đúng khi chạy 10 instance app. |
| Entity `PurchaseOrder` giữ `userId` dạng số, không dùng `@ManyToOne` | Tránh lazy loading và query thừa trên đường xử lý nóng. |
| Flyway quản lý schema, Hibernate chỉ `validate` | Schema có version, review được như code. `ddl-auto=update` không bao giờ dùng ở production. |

**Seed 100.000 user** (`V2__seed_data.sql`): dùng `WITH RECURSIVE` sinh dãy 1..100000 trong **1 câu INSERT**.
Chèn 100.000 câu INSERT riêng lẻ sẽ mất hàng phút vì mỗi câu là 1 round-trip mạng cộng 1 lần ghi log.

---

## 2. Race condition và Lost update

**Race condition:** kết quả phụ thuộc thứ tự chạy của các thread, và thứ tự đó không kiểm soát được.

Cách viết "ngây thơ" (`NaiveStrategy`):
```java
int stock = select available_stock;       // 1. đọc
if (stock > 0) update set available_stock = stock - 1;   // 2. ghi
```

Giữa bước 1 và bước 2 có **khoảng hở**:
```
Thời gian   T1 (user A)               T2 (user B)
   1        đọc stock = 5
   2                                  đọc stock = 5
   3        ghi stock = 4
   4        COMMIT
   5                                  ghi stock = 4   ← ghi đè kết quả của T1
   6                                  COMMIT
Kết quả: 2 đơn hàng, kho chỉ giảm 1  →  LOST UPDATE
```

**Vì sao `CHECK (available_stock >= 0)` không cứu được?** Vì giá trị ghi vào luôn hợp lệ (4 ≥ 0). Lỗi nằm ở chỗ ghi **giá trị tuyệt đối** tính từ dữ liệu cũ, chứ không phải ở giá trị âm.

> 💡 **MySQL khác PostgreSQL ở đây:** ở mức REPEATABLE READ, PostgreSQL phát hiện tình huống này và báo lỗi
> `could not serialize access due to concurrent update`. MySQL/InnoDB thì **im lặng ghi đè**.
> Câu này phân biệt được người hiểu sâu với người chỉ thuộc lý thuyết.

---

## 3. MVCC: consistent read và current read

InnoDB dùng **MVCC** (Multi-Version Concurrency Control): mỗi dòng có nhiều phiên bản, nên người đọc không chặn người ghi.

| Loại đọc | Câu lệnh | Đọc bản nào? | Có khoá? |
|---|---|---|---|
| **Consistent read** (snapshot) | `SELECT ...` thường | Snapshot lúc transaction đọc lần đầu | Không |
| **Current read** | `SELECT ... FOR UPDATE`, `UPDATE`, `DELETE` | Bản **mới nhất đã commit** | Có (X lock) |

Đây là chìa khoá để hiểu cả 5 chiến lược:
- `NAIVE` đọc bằng consistent read (có thể cũ) rồi ghi đè ⇒ **sai**.
- `PESSIMISTIC` đọc bằng current read và giữ khoá ⇒ **đúng**.
- `ATOMIC_UPDATE`: điều kiện `WHERE available_stock > 0` trong câu `UPDATE` được kiểm tra bằng current read ⇒ **đúng**.
- `OPTIMISTIC`: đọc snapshot, nhưng `UPDATE ... WHERE version = ?` dùng current read nên phát hiện được thay đổi ⇒ **đúng**.

**Isolation level mặc định:** MySQL là `REPEATABLE READ`, PostgreSQL là `READ COMMITTED`.

---

## 4. 5 chiến lược

Code: `backend/src/main/java/com/duong/flashsale/purchase/strategy/`. Áp dụng **Strategy pattern**: thêm cách mới chỉ cần thêm 1 class implement `PurchaseStrategy`.

### 4.1 NAIVE ❌
Đọc rồi ghi đè. Lost update như mục 2.

### 4.2 SYNCHRONIZED ❌
Xem mục 5. Đây là lỗi kinh điển, bị hỏi rất nhiều.

### 4.3 PESSIMISTIC ✅ (khoá bi quan)
```sql
SELECT * FROM flash_sale_item WHERE id = 1 FOR UPDATE;   -- lấy X lock, người khác phải chờ
UPDATE flash_sale_item SET available_stock = ...;
INSERT INTO orders ...;
COMMIT;                                                   -- nhả khoá
```
- **Ưu:** đúng tuyệt đối, chạy được nhiều instance app (khoá nằm ở DB).
- **Nhược:** mọi request **xếp hàng tuần tự** qua 1 dòng. Mỗi request giữ khoá suốt transaction (gồm cả INSERT order), cộng thêm thời gian chờ connection.
- **Rủi ro:** transaction dài, ví dụ gọi API thanh toán trong lúc giữ khoá, sẽ làm cả hệ thống đứng. **Không bao giờ gọi network trong lúc giữ khoá DB.**

### 4.4 OPTIMISTIC ✅ nhưng không hợp flash sale (khoá lạc quan)
```sql
SELECT available_stock, version FROM flash_sale_item WHERE id = 1;  -- version = 7
UPDATE flash_sale_item SET available_stock = ..., version = 8
 WHERE id = 1 AND version = 7;                                       -- 0 dòng => có người sửa trước => retry
```
- Hibernate tự làm việc này khi entity có `@Version`.
- **Retry phải ở transaction MỚI** (`TransactionTemplate`), vì transaction cũ vẫn giữ snapshot cũ.
- **Exponential backoff + jitter:** nếu mọi thread cùng retry sau 10ms thì lại đụng nhau tiếp (thundering herd), nên phải random hoá thời gian chờ.
- **Hợp với:** dữ liệu ít tranh chấp như sửa profile hay sửa bài viết.
- **Không hợp flash sale:** 500 request cùng sửa 1 dòng thì chỉ 1 thắng, 499 phải retry, cực kỳ lãng phí. Có thể xảy ra `RETRY_EXHAUSTED` **trong khi kho vẫn còn hàng**, khách bị từ chối oan.

### 4.5 ATOMIC_UPDATE ✅ tốt nhất ở tầng DB
```sql
UPDATE flash_sale_item
   SET available_stock = available_stock - 1
 WHERE id = 1 AND available_stock > 0;
-- affected rows = 1 → mua được; = 0 → hết hàng
```
- "Kiểm tra" và "trừ" nằm trong **1 câu lệnh** nên không có khoảng hở.
- Không cần retry, giữ khoá ngắn nhất có thể.
- **Giới hạn:** mọi request vẫn chạm DB. Với 20.000 RPS, DB thành nút cổ chai, kể cả request hỏi khi đã hết hàng. Phase 2 xử lý việc này bằng Redis.

### Bảng tổng kết

| | Đúng? | Nhiều instance? | Throughput | Dùng khi |
|---|---|---|---|---|
| NAIVE | ❌ | ❌ | cao (nhưng sai) | không bao giờ |
| SYNCHRONIZED | ❌ | ❌ | thấp | không bao giờ |
| PESSIMISTIC | ✅ | ✅ | thấp | tranh chấp cao, logic phức tạp cần đọc rồi quyết định |
| OPTIMISTIC | ✅ | ✅ | rất thấp khi tranh chấp cao | tranh chấp thấp |
| ATOMIC_UPDATE | ✅ | ✅ | cao | điều kiện viết được trong 1 câu SQL |

---

## 5. Bẫy: synchronized + @Transactional

```java
@Transactional
public synchronized PurchaseResult purchase(...) { ... }
```
Nhìn thì tưởng an toàn, nhưng `@Transactional` hoạt động qua **proxy bọc bên ngoài**:
```
Proxy:  BEGIN TRANSACTION
          ┌─ synchronized: lock
          │    đọc stock, ghi stock-1
          └─ synchronized: unlock     ← thread B vào ngay tại đây
Proxy:  COMMIT                        ← nhưng dữ liệu của A tới đây mới commit
```
Thread B đọc được giá trị **chưa commit của A** (tức giá trị cũ) nên vẫn lost update.

**Hai lỗi chồng lên nhau:**
1. Khoá nhả **trước** commit.
2. `synchronized` chỉ có tác dụng **trong 1 JVM**. Deploy 2 instance là vô dụng hoàn toàn.

**Liên quan, hay bị hỏi:** gọi method `@Transactional` từ method khác **trong cùng class** (self-invocation) thì transaction **không chạy**, vì lời gọi không đi qua proxy.

---

## 6. Chống mua trùng

`PurchaseService` có 2 lớp bảo vệ:
1. **Fast path:** `existsByUserIdAndItemId` chặn sớm người đã mua, không đụng vào dòng tồn kho đang nóng. Câu này chỉ đọc index `uk_orders_user_item` (covering index).
2. **Chốt chặn thật:** `UNIQUE (user_id, item_id)`. Hai request cùng user có thể cùng lọt qua bước 1, nhưng INSERT thứ hai sẽ vi phạm unique key. Transaction rollback **kể cả phần trừ kho** nên kho được hoàn tự động.

> **Nguyên tắc:** kiểm tra ở tầng app chỉ để **tối ưu**. Tính đúng đắn phải được **ràng buộc ở DB**.

---

## 7. Deadlock do foreign key

Lỗi thực tế, rất khó debug nếu chưa gặp:
- `INSERT INTO orders (item_id = 1)` có FK nên InnoDB đặt **S lock** (shared) lên dòng `flash_sale_item id=1` để chắc rằng dòng đó không bị xoá.
- Hibernate mặc định flush **INSERT trước UPDATE**.

```
T1: INSERT order → S lock item#1     T2: INSERT order → S lock item#1   (S với S không xung đột)
T1: UPDATE item#1 → cần X lock, chờ T2 nhả S
T2: UPDATE item#1 → cần X lock, chờ T1 nhả S   →  DEADLOCK
```
**Cách xử lý trong code:** gọi `itemRepository.flush()` để UPDATE (lấy X lock) **trước**, rồi mới INSERT order. Xem `PessimisticLockStrategy` và `OptimisticLockStrategy`.

**Bài học chung:** luôn lấy khoá **theo cùng một thứ tự**. Nhiều hệ thống lớn bỏ hẳn FK ở các bảng ghi nhiều và kiểm tra ở tầng app; đó là đánh đổi giữa tính toàn vẹn và hiệu năng.

Soi deadlock: `SHOW ENGINE INNODB STATUS\G` → mục `LATEST DETECTED DEADLOCK`.

---

## 8. Connection pool, virtual threads, fail fast

**Virtual threads** (`spring.threads.virtual.enabled=true`): mỗi request chạy trên 1 virtual thread rất rẻ, nên Tomcat nhận được hàng chục nghìn request đồng thời mà không cần 10.000 OS thread.

**Nhưng** DB chỉ có `maximum-pool-size=20` connection. 10.000 virtual thread vẫn phải **xếp hàng chờ 20 connection**. Virtual threads không làm DB nhanh hơn; chúng chỉ giúp app không chết vì thiếu thread.

**Pool size bao nhiêu là đủ?** Không phải càng lớn càng tốt. Công thức tham khảo của HikariCP: `connections ≈ (số core CPU của DB × 2) + số ổ đĩa`. Pool quá lớn làm DB tốn thời gian chuyển ngữ cảnh và tranh chấp khoá nhiều hơn. Hãy đo: chạy load test với pool 10, 20, 50 rồi so sánh.

**Fail fast:** `connection-timeout: 5000`. Hết connection thì sau 5s trả **503 "Server busy"** thay vì treo 30s. Client và load balancer thấy 503 sẽ biết cần retry hoặc giảm tải. Xem `GlobalExceptionHandler`.

**Tắt Open Session In View** (`open-in-view: false`): nếu bật, mỗi request HTTP giữ 1 connection DB từ đầu đến cuối, kể cả lúc serialize JSON. Pool cạn rất nhanh.

---

## 9. Cách kiểm chứng

### 9.1 Test đồng thời (JUnit + MySQL thật)
`backend/src/test/java/.../PurchaseConcurrencyTest.java`
- `CountDownLatch` là "phát súng xuất phát": 300 thread chờ sẵn, rồi được thả **cùng lúc** để tranh chấp cao nhất.
- Test với **MySQL thật**, không dùng H2. H2 khoá khác MySQL, test xanh trên H2 không chứng minh được gì.
- Test chứng minh **cả hai chiều**: chiến lược đúng thì bán đúng 50/50; chiến lược sai thì **phải** lộ lỗi lost update.

```bash
cd backend
./mvnw test
```

### 9.2 Load test (Node, không cần cài k6)
`load-test/flash-sale-load.mjs`
```bash
cd load-test
node flash-sale-load.mjs --all --requests 20000 --concurrency 500
```
Mỗi lượt: reset kho, bắn N request (mỗi request 1 user khác nhau), đo p50/p95/p99 và throughput, rồi **đọc lại DB** để kiểm tra bán lố.

**p99 là gì?** 99% request nhanh hơn mức này. Trung bình (avg) che giấu những request rất chậm; p99 mới cho thấy trải nghiệm của người dùng xui nhất.

**Lưu ý trung thực:** chạy trên laptop thì app, MySQL và load tester tranh nhau CPU. Con số tuyệt đối **không** phản ánh production; **phép so sánh giữa các chiến lược** thì có giá trị.

---

## 10. Kết quả benchmark

> Sẽ được điền sau khi chạy `node flash-sale-load.mjs --all` trên máy local.

---

## 11. Câu hỏi phỏng vấn

<details><summary><b>Q1. Làm sao chống bán lố trong flash sale?</b></summary>

Trả lời theo tầng:
1. **DB:** atomic conditional update `UPDATE ... WHERE stock > 0`; hoặc `SELECT FOR UPDATE` nếu logic phức tạp.
2. **Cache:** trừ kho trong Redis bằng Lua script (nguyên tử), DB chỉ nhận đơn đã chắc chắn có hàng.
3. **Async:** đẩy đơn vào queue, worker ghi DB với tốc độ DB chịu được (peak shaving).
4. **Chặn sớm:** rate limit, waiting room, cờ "hết hàng" trong bộ nhớ để từ chối mà không cần chạm DB.
</details>

<details><summary><b>Q2. Optimistic vs Pessimistic lock, khi nào dùng cái nào?</b></summary>

Optimistic khi **tranh chấp thấp**: không khoá nên nhanh, xung đột hiếm nên ít retry.
Pessimistic khi **tranh chấp cao**: retry liên tục còn tốn hơn xếp hàng.
Flash sale tranh chấp cực cao nên optimistic là lựa chọn tệ.
</details>

<details><summary><b>Q3. Vì sao synchronized không chống được race condition ở đây?</b></summary>

(1) Khoá nhả trước khi transaction commit, vì proxy `@Transactional` bọc bên ngoài.
(2) Chỉ có tác dụng trong 1 JVM, nhiều instance là hỏng.
</details>

<details><summary><b>Q4. REPEATABLE READ của MySQL có chống được lost update không?</b></summary>

**Không** với kiểu read-modify-write bằng `SELECT` thường. MySQL không báo lỗi serialization như PostgreSQL. Phải dùng `FOR UPDATE`, atomic update, hoặc cột version.
</details>

<details><summary><b>Q5. User bấm "Mua" 2 lần liên tiếp thì sao?</b></summary>

Unique key `(user_id, item_id)` ở DB. Lần 2 vi phạm unique key nên transaction rollback, kể cả phần trừ kho.
Với API thanh toán tổng quát, dùng **idempotency key** do client sinh và gửi trong header.
</details>

<details><summary><b>Q6. Deadlock là gì, xử lý thế nào?</b></summary>

2 transaction giữ khoá mà mỗi bên cần, và chờ nhau mãi mãi. InnoDB tự phát hiện rồi rollback 1 bên (nạn nhân).
Phòng tránh: lấy khoá theo thứ tự cố định, transaction ngắn, index đúng (để không khoá thừa).
App phải **retry** khi bị chọn làm nạn nhân.
Ví dụ thật trong dự án: FK + thứ tự flush của Hibernate (mục 7).
</details>

<details><summary><b>Q7. Virtual threads có làm hệ thống nhanh hơn không?</b></summary>

Không làm từng request nhanh hơn. Chúng giúp phục vụ **nhiều request chờ IO đồng thời** với ít tài nguyên.
Nút cổ chai vẫn là connection pool và DB.
</details>

---

## 12. Lộ trình

- [x] **Phase 1 — Database locking:** 5 chiến lược, test đồng thời, load test, đo đạc.
- [ ] **Phase 2 — Redis:** trừ kho bằng Lua script, cờ hết hàng trong bộ nhớ, rate limit (token bucket).
- [ ] **Phase 3 — Bất đồng bộ:** nhận đơn qua Redis Streams, worker ghi DB, Transactional Outbox.
- [ ] **Phase 4 — Giữ chỗ và hết hạn:** giữ vé 10 phút chờ thanh toán, quá hạn tự trả về kho.
- [ ] **Phase 5 — Waiting room:** hàng đợi ảo bằng Redis Sorted Set, cho vào theo lô.
