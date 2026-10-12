-- Sinh 100.000 user bằng recursive CTE: 1 câu INSERT, nhanh hơn rất nhiều so với 100.000 câu INSERT lẻ.
-- Mặc định MySQL chỉ cho đệ quy 1000 tầng nên phải nới giới hạn cho session này.
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO users (username, email)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT CONCAT('user', n), CONCAT('user', n, '@example.com')
FROM seq;

INSERT INTO flash_sale_item (id, name, price, total_stock, available_stock)
VALUES (1, 'Vé concert Mùa Hè 2026 - Hạng VIP', 2500000, 1000, 1000);
